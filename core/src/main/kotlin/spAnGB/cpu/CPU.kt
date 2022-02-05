@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu

import spAnGB.cpu.arm.ARMOpFactory
import spAnGB.cpu.thumb.ThumbOpFactory
import spAnGB.memory.Bus
import spAnGB.utils.hex


const val CLOCK_SPEED = 16777216

class CPU(
    b: Bus
) {
    @JvmField
    val bus: Bus = b

    val mmio = bus.mmio

    val registers = IntArray(16)

    @JvmField
    val registerBanks = Array(4) { IntArray(3) }
    @JvmField
    val fastIrqRegisters = IntArray(7)
    @JvmField
    var fastIrqSpsr: Int = 0
    @JvmField
    val _registers = IntArray(7)

    @JvmField
    val pipeline = IntArray(3)

    inline val pipelineHead: Int get() = pipeline[2]

    @JvmField
    var cpsr: Int = 0
    var spsr: Int = 0

    @JvmField
    var shifterCarry = false

    @JvmField
    val ime = mmio.ime
    @JvmField
    val halt = mmio.halt

    inline var pc: Int
        get() = registers[15]
        set(value) { registers[15] = value }

    inline var lr: Int
        get() = registers[14]
        set(value) { registers[14] = value }

    inline operator fun get(flag: CPUFlag): Boolean = (cpsr and flag.mask) != 0
    inline operator fun set(flag: CPUFlag, value: Boolean) {
        cpsr = cpsr and (flag.mask.inv())
        if (value) cpsr = cpsr or flag.mask
    }

    var state = CPUState.ARM
    var mode = CPUMode.System

    @JvmField
    val lutARM = Array(4096) { ARMOpFactory(it).execute }
    @JvmField
    val lutThumb = Array(1024) { ThumbOpFactory(it).execute }

    init {
        registers[0] = 0x08000000
        registers[1] = 0xEA
        registers[13] = 0x3007F00
        pc = 0x08000000
        registerBanks[CPUMode.Supervisor][0] = 0x3007FE0
        registerBanks[CPUMode.Interrupt][0] = 0x3007FA0
        cpsr = 0x6000001F

        armFill()
    }

    inline fun setRegister(index: Int, value: Int) {
        registers[index] = value
        if (index == 15) {
            if (state == CPUState.ARM) {
                armRefill()
            } else {
                thumbRefill()
            }
        }
    }


    fun checkCondition(op: Int): Boolean = when (op) {
        0x0 -> this[CPUFlag.Z]
        0x1 -> !this[CPUFlag.Z]
        0x2 -> this[CPUFlag.C]
        0x3 -> !this[CPUFlag.C]
        0x4 -> this[CPUFlag.N]
        0x5 -> !this[CPUFlag.N]
        0x6 -> this[CPUFlag.V]
        0x7 -> !this[CPUFlag.V]
        0x8 -> this[CPUFlag.C] && !this[CPUFlag.Z]
        0x9 -> !this[CPUFlag.C] || this[CPUFlag.Z]
        0xA -> this[CPUFlag.N] == this[CPUFlag.V]
        0xB -> this[CPUFlag.N] != this[CPUFlag.V]
        0xC -> !this[CPUFlag.Z] && (this[CPUFlag.N] == this[CPUFlag.V])
        0xD -> this[CPUFlag.Z] || (this[CPUFlag.N] != this[CPUFlag.V])
        0xE -> true
        0xF -> false
        else -> throw IllegalStateException("Undefined condition: ${op.hex}")
    }

    fun tick() {
        handlePendingInterrupts()
        if (halt.isHalted) return

        val op = pipelineHead
        if (op == 0) TODO("[CPU] OpCode 0 at +- ${pc.hex}")

        if (state == CPUState.THUMB) {
            lutThumb[(op ushr 6) and 0x3FF](this, op)
        } else if (checkCondition(op ushr 28)) {
            lutARM[((op and 0xF0) ushr 4) or ((op ushr 16) and 0xFF0)](this, op)
        }

        if (state == CPUState.ARM) {
            armStep()
        } else {
            thumbStep()
        }
    }

    fun changeState(stateBit: Int) {
        state = CPUState.from(stateBit)
        this[CPUFlag.T] = state == CPUState.THUMB

        when (state) {
            CPUState.ARM -> armRefill()
            CPUState.THUMB -> thumbRefill()
        }
    }

    fun setCPUMode(value: CPUMode) {  // page 55
        if (mode != value) {
            when (mode) {  // save state
                CPUMode.User, CPUMode.System -> {
                    registers.copyInto(
                        _registers,
                        0, 8, 15
                    )
                }
                CPUMode.FastInterrupt -> {
                    registers.copyInto(
                        fastIrqRegisters,
                        0, 8, 15
                    )
                    fastIrqSpsr = spsr
                }
                else -> {
                    registers.copyInto(
                        _registers,
                        0, 8, 13
                    )
                    registerBanks[mode][0] = registers[13]
                    registerBanks[mode][1] = registers[14]
                    registerBanks[mode][2] = spsr
                }
            }

            when (value) {  // restore state
                CPUMode.User, CPUMode.System -> {
                    _registers.copyInto(
                        registers,
                        8, 0, 7
                    )

//                    spsr = 0  // TODO: correctness of this?
                }
                CPUMode.FastInterrupt -> {
                    fastIrqRegisters.copyInto(
                        registers,
                        8, 0, 7
                    )
                    spsr = fastIrqSpsr
                }
                else -> {
                    _registers.copyInto(
                        registers,
                        8, 0, 5
                    )

                    registers[13] = registerBanks[value][0]
                    registers[14] = registerBanks[value][1]
                    spsr = registerBanks[value][2]
                }
            }

            cpsr = (cpsr and 0x1F.inv()) or value.mask
            mode = value
        }
    }

    fun handlePendingInterrupts() {
        if (!get(CPUFlag.I) && ime.enabled) {
            val pending = (mmio.ir.value and mmio.ie.value) and 0x3FFF

            if (pending != 0) {
                val link = pc - if (state == CPUState.ARM) 4 else 0
                val cpsrCopy = cpsr

                setCPUMode(CPUMode.Interrupt)

                pc = 0x18
                lr = link
                spsr = cpsrCopy
                this[CPUFlag.I] = true
                this[CPUFlag.T] = false
                state = CPUState.ARM

                flush()
                armFill()

                halt.isHalted = false
            }
        }
    }

    fun flush() {
        pipeline[0] = 0
        pipeline[1] = 0
        pipeline[2] = 0
    }

    fun thumbRefill() {
        pc = pc and (1.inv())
        pipeline[1] = bus.read16(pc).toInt()
        pc += 2
        pipeline[0] = bus.read16(pc).toInt()
    }

    fun thumbStep() {
        pipeline[2] = pipeline[1]
        pipeline[1] = pipeline[0]
        pc += 2
        pipeline[0] = bus.read16(pc).toInt()
    }

    fun armFill() {
        pipeline[2] = bus.read32(pc)
        pc += 4
        pipeline[1] = bus.read32(pc)
        pc += 4
        pipeline[0] = bus.read32(pc)
    }

    fun armRefill() {
        pc = pc and (3.inv())
        pipeline[1] = bus.read32(pc)
        pc += 4
        pipeline[0] = bus.read32(pc)
    }

    fun armStep() {
        pipeline[2] = pipeline[1]
        pipeline[1] = pipeline[0]
        pc += 4
        pipeline[0] = bus.read32(pc)
    }
}