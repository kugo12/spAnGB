@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu

import spAnGB.cpu.arm.ARMOpFactory
import spAnGB.cpu.thumb.ThumbOpFactory
import spAnGB.memory.Bus
import spAnGB.utils.hex
import spAnGB.utils.swapWith


fun undefinedThumbInstruction(op: Int) = ThumbInstruction(
    { "Undefined THUMB ${it.hex}" },
    { TODO("Undefined THUMB instruction ${op.hex}/${it.hex} @ ${pc.hex}") }
)

fun undefinedArmInstruction(op: Int) = ARMInstruction(
    { "Undefined ARM ${it.hex}" },
    { TODO("Undefined ARM instruction ${op.hex}/${it.hex} @ ${pc.hex}") }
)

class CPU(
    val bus: Bus
) {
    @JvmInline
    value class Registers(val content: IntArray = IntArray(16)) {
        inline operator fun get(index: Int): Int = content[index]
    }

    val mmio = bus.mmio

    val registers = Registers()
    val registerBanks = Array(4) { IntArray(3) }

    val fastIrqRegisters = IntArray(7)
    var fastIrqSpsr: Int = 0

    val _registers = IntArray(7)
    var cpsr: Int = 0
    var spsr: Int = 0

    inline var pc: Int
        get() = registers.content[15]
        set(value) { registers.content[15] = value }

    inline var lr: Int
        get() = registers.content[14]
        set(value) { registers.content[14] = value }

    inline operator fun get(flag: CPUFlag): Boolean = (cpsr and flag.mask) != 0
    inline operator fun set(flag: CPUFlag, value: Boolean) {
        cpsr = cpsr and (flag.mask.inv())
        if (value) cpsr = cpsr or flag.mask
    }

    var state = CPUState.ARM
    var mode = CPUMode.System
    val pipeline = Pipeline()

    val lutARM = Array(4096) { ARMOpFactory(it).execute }
    val lutThumb = Array(1024) { ThumbOpFactory(it).execute }

    init {
        registers[0] = 0x08000000
        registers[1] = 0xEA
        registers[13] = 0x3007F00
        pc = 0x08000000
        registerBanks[CPUMode.Supervisor][0] = 0x3007FE0
        registerBanks[CPUMode.Interrupt][0] = 0x3007FA0
        cpsr = 0x6000001F

        pipeline.armFill(this)
    }

    inline operator fun Registers.set(index: Int, value: Int) {
        content[index] = value
        if (index == 15) {
            when (state) {
                CPUState.ARM -> pipeline.armRefill(this@CPU)
                CPUState.THUMB -> pipeline.thumbRefill(this@CPU)
            }
        }
    }

    inline fun checkCondition(op: Int): Boolean = when (op) {
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

    @Suppress("ReplaceGetOrSet")
    fun tick() {
        handlePendingInterrupts()

        val op = pipeline.head
        if (op == 0) TODO()

        when (state) {
            CPUState.ARM ->
                if (checkCondition(op ushr 28))
                    lutARM
                        .get(((op and 0xF0) ushr 4) or ((op ushr 16) and 0xFF0))
                        .invoke(this, op)
            CPUState.THUMB ->
                lutThumb
                    .get((op ushr 6) and 0x3FF)
                    .invoke(this, op)
        }

        when (state) {
            CPUState.ARM -> pipeline.armStep(this)
            CPUState.THUMB -> pipeline.thumbStep(this)
        }
    }

    inline fun changeState(stateBit: Int) {
        state = CPUState.from(stateBit)
        this[CPUFlag.T] = state == CPUState.THUMB

        when (state) {
            CPUState.ARM -> pipeline.armRefill(this)
            CPUState.THUMB -> pipeline.thumbRefill(this)
        }
    }

    fun setCPUMode(value: CPUMode) {  // page 55
        if (mode != value) {
            when (mode) {  // save state
                CPUMode.User, CPUMode.System -> {
                    registers.content.copyInto(
                        _registers,
                        0, 8, 15
                    )
                }
                CPUMode.FastInterrupt -> {
                    registers.content.copyInto(
                        fastIrqRegisters,
                        0, 8, 15
                    )
                    fastIrqSpsr = spsr
                }
                else -> {
                    registers.content.copyInto(
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
                    _registers.swapWith(
                        registers.content,
                        8, 0, 6
                    )

//                    spsr = 0  // TODO: correctness of this?
                }
                CPUMode.FastInterrupt -> {
                    fastIrqRegisters.swapWith(
                        registers.content,
                        8, 0, 6
                    )
                    spsr = fastIrqSpsr
                }
                else -> {
                    _registers.copyInto(
                        registers.content,
                        8, 0, 5
                    )

                    registers[13] = registerBanks[value][0]
                    registers[14] = registerBanks[value][1]
                    spsr = registerBanks[value][2]
                }
            }

            cpsr = (cpsr and 0xFFFFFFE0u.toInt()) or value.mask
            mode = value
        }
    }

    fun handlePendingInterrupts() {
        if (!get(CPUFlag.I) && mmio.ime.enabled) {
            val pending = (mmio.ir.value and mmio.ie.value) and 0xFFFF

            if (pending != 0) {

                val link = pc - if (state == CPUState.ARM) 4 else 2
                val cpsrCopy = cpsr

                setCPUMode(CPUMode.Interrupt)
                lr = link
                pc = 0x18
                spsr = cpsr
                this[CPUFlag.I] = true
                changeState(0)
                pipeline.armStep(this)
            }
        }
    }
}