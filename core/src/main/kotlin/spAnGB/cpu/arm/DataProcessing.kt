@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu.arm

import spAnGB.cpu.*
import spAnGB.memory.AccessType
import spAnGB.utils.bit
import spAnGB.utils.hex
import spAnGB.utils.toInt
import spAnGB.utils.uLong

val dataProcessingDsl = DataProcessingDsl()

class DataProcessingDsl {
    lateinit var cpu: CPU
    @JvmField
    var instruction: Int = 0
    @JvmField
    var firstOperand: Int = 0
    @JvmField
    var secondOperand: Int = 0

    @JvmField
    var destinationRegister: Int = 0
    @JvmField
    var result: Int = 0

    fun initialize(cpu: CPU) {
        this.cpu = cpu
        this.instruction = cpu.instr

        destinationRegister = (instruction ushr 12) and 0xF
        secondOperand = when {
            !(instruction bit 25) && instruction bit 4 -> {
                cpu.bus.idle()
                cpu.prefetchAccess = AccessType.NonSequential
                cpu.pc += 4
                val tmp = cpu.operand(instruction ushr 4, cpu.registers[instruction and 0xF])
                firstOperand = cpu.registers[(instruction ushr 16) and 0xF]
                cpu.pc -= 4
                tmp
            }
            instruction bit 25 -> {
                firstOperand = cpu.registers[(instruction ushr 16) and 0xF]
                cpu.barrelShifterRotateRight(instruction and 0xFF, (instruction and 0xF00) ushr 7)
            }
            else -> {
                firstOperand = cpu.registers[(instruction ushr 16) and 0xF]
                cpu.operand(instruction ushr 4, cpu.registers[instruction and 0xF])
            }
        }
    }


    inline fun perform(func: DataProcessingDsl.() -> Int) {
        result = func()
        cpu.setRegister(destinationRegister, result)
    }

    inline fun performWithoutDestination(func: DataProcessingDsl.() -> Int) {
        result = func()
        if (destinationRegister == 15) {
            println("[ARM] performWithoutDestination: destination == 15")

            cpu.setCPUMode(CPUMode.byMask[cpu.spsr and 0x1F]!!)
            cpu.state = if (cpu.spsr and CPUFlag.T.mask != 0) CPUState.THUMB else CPUState.ARM
            cpu.cpsr = cpu.spsr
        }
    }

    inline fun setFlags(func: DataProcessingDsl.() -> Unit) {
        if (instruction bit 20) {
            N = result < 0
            Z = result == 0
            func()
        }
    }

    inline fun setFlagsWithShifterCarry(func: DataProcessingDsl.() -> Unit = {}) {
        if (instruction bit 20) {
            N = result < 0
            Z = result == 0
            C = cpu.shifterCarry
            func()
        }
    }

    inline fun checkDestinationPC(): Boolean {
        if (destinationRegister == 15) {
            cpu.apply {
                pc = result
                val s = spsr
                setCPUMode(CPUMode.byMask[s and 0x1F]!!)
                changeState((s and CPUFlag.T.mask != 0).toInt())
                cpsr = s
            }
        }

        return destinationRegister == 15
    }

    inline fun overflow(op2: Int = secondOperand) {
        V = (firstOperand xor op2).inv() and (firstOperand xor result) < 0
    }

    inline fun subOverflow(op1: Int = firstOperand, op2: Int = secondOperand) {
        V = (op1 xor op2) and (op2 xor result).inv() < 0
    }

    inline fun dumbCarry(func: DataProcessingDsl.() -> Long) {  // TODO
        C = func() bit 32
    }

    inline fun dumbBorrow(func: DataProcessingDsl.() -> Long) {  // TODO
        C = !(func() bit 32)
    }

    inline val carry: Int get() = C.toInt()

    inline var N
        get() = cpu[CPUFlag.N]
        set(value) {
            cpu[CPUFlag.N] = value
        }

    inline var Z
        get() = cpu[CPUFlag.Z]
        set(value) {
            cpu[CPUFlag.Z] = value
        }

    inline var C
        get() = cpu[CPUFlag.C]
        set(value) {
            cpu[CPUFlag.C] = value
        }

    inline var V
        get() = cpu[CPUFlag.V]
        set(value) {
            cpu[CPUFlag.V] = value
        }
}


private inline fun instruction(crossinline func: DataProcessingDsl.() -> Unit): CPU.() -> Unit =
    {
        dataProcessingDsl.initialize(this)
        dataProcessingDsl.func()
    }

val armAnd = ARMInstruction(
    { "And" },
    instruction {
        perform { firstOperand and secondOperand }
        setFlagsWithShifterCarry()
    }
)

val armEor = ARMInstruction(
    { "Eor" },
    instruction {
        perform { firstOperand xor secondOperand }
        setFlagsWithShifterCarry()
    }
)

val armTst = ARMInstruction(
    { "Tst" },
    instruction {
        performWithoutDestination { firstOperand and secondOperand }
        setFlagsWithShifterCarry()
    }
)

val armTeq = ARMInstruction(
    { "Teq" },
    instruction {
        performWithoutDestination { firstOperand xor secondOperand }
        setFlagsWithShifterCarry()
    }
)

val armOrr = ARMInstruction(
    { "Orr" },
    instruction {
        perform { firstOperand or secondOperand }
        setFlagsWithShifterCarry()
    }
)

val armMov = ARMInstruction(
    { "Mov" },
    instruction {
        result = secondOperand

        setFlagsWithShifterCarry {
            if (checkDestinationPC()) return@instruction
        }

        cpu.setRegister(destinationRegister,  result)
    }
)

val armBic = ARMInstruction(
    { "Bic" },
    instruction {
        perform { firstOperand and (secondOperand.inv()) }
        setFlagsWithShifterCarry()
    }
)

val armMvn = ARMInstruction(
    { "Mvn" },
    instruction {
        perform { secondOperand.inv() }
        setFlagsWithShifterCarry()
    }
)

val armSub = ARMInstruction(
    { "Sub" },
    instruction {
        result = firstOperand - secondOperand
        setFlags {
            subOverflow()
            dumbBorrow { firstOperand.uLong - secondOperand.uLong }

            if (checkDestinationPC()) return@instruction
        }

        cpu.setRegister(destinationRegister, result)
    }
)

val armRsb = ARMInstruction(
    { "Rsb" },
    instruction {
        perform { secondOperand - firstOperand }
        setFlags {
            subOverflow(secondOperand, firstOperand)
            dumbBorrow { secondOperand.uLong - firstOperand.uLong }
        }
    }
)

val armAdd = ARMInstruction(
    { "Add" },
    instruction {
        perform { firstOperand + secondOperand }
        setFlags {
            overflow()
            dumbCarry { firstOperand.uLong + secondOperand.uLong }
        }
    }
)

val armAdc = ARMInstruction(
    { "Adc" },
    instruction {
        perform { firstOperand + secondOperand + carry }
        setFlags {
            overflow()
            dumbCarry { firstOperand.uLong + secondOperand.uLong + carry.uLong }
        }
    }
)

val armSbc = ARMInstruction(
    { "Sbc" },
    instruction {
        perform { firstOperand - secondOperand - 1 + carry }
        setFlags {
            subOverflow()
            dumbBorrow { firstOperand.uLong - secondOperand.uLong - 1L + carry.uLong }
        }
    }
)

val armRsc = ARMInstruction(
    { "Rsc" },
    instruction {
        perform { secondOperand - firstOperand - 1 + carry }
        setFlags {
            subOverflow(op1 = secondOperand, op2 = firstOperand)
            dumbBorrow { secondOperand.uLong - firstOperand.uLong - 1L + carry.uLong }
        }
    }
)

val armCmp = ARMInstruction(
    { "Cmp" },
    instruction {
        performWithoutDestination { firstOperand - secondOperand }
        setFlags {
            subOverflow()
            dumbBorrow { firstOperand.uLong - secondOperand.uLong }
        }
    }
)

val armCmn = ARMInstruction(
    { "Cmn" },
    instruction {
        performWithoutDestination { firstOperand + secondOperand }
        setFlags {
            overflow()
            dumbCarry { firstOperand.uLong + secondOperand.uLong }
        }
    }
)

val armMrs = ARMInstruction(
    { "Mrs" },
    instruction {
        perform {
            when (instruction bit 22) {
                true -> cpu.spsr
                false -> cpu.cpsr
            }
        }
    }
)

val armMsr = ARMInstruction(
    { "Msr" },
    {
        val src = when (instr bit 25) {
            true -> (instr and 0xFF).rotateRight((instr and 0xF00) ushr 7)
            false -> registers[instr and 0xF]
        }

        val mask: Int = when (mode) {
            CPUMode.User -> 0xF0000000u.toInt()
            else -> (0xFF * (instr bit 16).toInt())
                .or(0xFF00 * (instr bit 17).toInt())
                .or(0xFF0000 * (instr bit 18).toInt())
                .or(0xFF000000u.toInt() * (instr bit 19).toInt())
        }

        when (instr bit 22) {
            true -> {
                spsr = (spsr and mask.inv()) or (src and mask)
            }
            false -> {
                cpsr = (cpsr and mask.inv()) or (src and mask)
                setCPUMode(CPUMode.byMask[cpsr and 0x1F]!!)  // TODO: changing mode by mask?
            }
        }
    }
)