package spAnGB.cpu.thumb

import spAnGB.cpu.CPU
import spAnGB.cpu.CPUFlag
import spAnGB.cpu.CPUInstruction
import spAnGB.cpu.ThumbInstruction
import spAnGB.cpu.arm.barrelShifterArithmeticRight
import spAnGB.cpu.arm.barrelShifterLogicalLeft
import spAnGB.cpu.arm.barrelShifterLogicalRight
import spAnGB.cpu.arm.barrelShifterRotateRight
import spAnGB.utils.bit
import spAnGB.utils.hex
import spAnGB.utils.toInt
import spAnGB.utils.uLong

class DataProcessingDsl(val cpu: CPU, instruction: Int) {
    @JvmField
    val operand: Int = cpu.registers[(instruction ushr 3) and 0x7]
    @JvmField
    val destinationRegister: Int = instruction and 0x7
    @JvmField
    var result: Int = 0

    @JvmField
    val dest = cpu.registers[destinationRegister]

    inline var destination: Int
        get() = dest
        set(value) {
            cpu.registers[destinationRegister] = value
        }

    inline fun perform(func: DataProcessingDsl.() -> Int) {
        val tmp = func()
        result = tmp
        destination = tmp
        N = tmp < 0
        Z = tmp == 0
    }

    inline fun performWithoutDestination(func: DataProcessingDsl.() -> Int) {
        result = func()
        N = result < 0
        Z = result == 0
    }

    inline fun performShift(func: CPU.(Int, Int) -> Int) {
        val tmp = cpu.func(destination, operand and 0xFF)
        destination = tmp
        C = cpu.shifterCarry
        N = tmp < 0
        Z = tmp == 0
    }

    inline fun overflow() {
        V = (operand xor destination).inv() and (destination xor result) < 0
    }

    inline fun dumbCarry(func: DataProcessingDsl.() -> Long) {  // TODO
        C = func() bit 32
    }

    inline fun subOverflow(dest: Int = this.dest, op: Int = operand) {
        V = (dest xor op) and (op xor result).inv() < 0
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

private inline fun instruction(crossinline func: DataProcessingDsl.() -> Unit): CPU.(Int) -> Unit = { instr ->
    DataProcessingDsl(this, instr).func()
}

private inline fun simpleInstruction(crossinline func: DataProcessingDsl.() -> Int): CPU.(Int) -> Unit = { instr ->
    DataProcessingDsl(this, instr).perform(func)
}

private inline fun simpleInstructionWithoutDestination(crossinline func: DataProcessingDsl.() -> Int): CPU.(Int) -> Unit = { instr ->
    DataProcessingDsl(this, instr).performWithoutDestination(func)
}

private inline fun shiftInstruction(crossinline func: CPU.(Int, Int) -> Int): CPU.(Int) -> Unit = { instr ->
    DataProcessingDsl(this, instr).performShift(func)
}

val thumbAnd = ThumbInstruction(
    { "And" },
    simpleInstruction { destination and operand }
)
val thumbEor = ThumbInstruction(
    { "Eor" },
    simpleInstruction { destination xor operand }
)
val thumbOrr = ThumbInstruction(
    { "Orr" },
    simpleInstruction { destination or operand }
)
val thumbBic = ThumbInstruction(
    { "Bic" },
    simpleInstruction { destination and (operand.inv()) }
)
val thumbMvn = ThumbInstruction(
    { "Mvn" },
    simpleInstruction { operand.inv() }
)
val thumbTst = ThumbInstruction(
    { "Tst" },
    simpleInstructionWithoutDestination { destination and operand }
)

val thumbNeg = ThumbInstruction(
    { "Neg" },
    simpleInstruction {
        C = true
        V = false
        -operand
    }
)
val thumbMul = ThumbInstruction(
    { "Mul" },
    simpleInstruction {
        C = false
        destination * operand
    }
)

val thumbLsl = ThumbInstruction(
    { "Lsl" },
    shiftInstruction(CPU::barrelShifterLogicalLeft)
)
val thumbLsr = ThumbInstruction(
    { "Lsr" },
    shiftInstruction(CPU::barrelShifterLogicalRight)
)
val thumbAsr = ThumbInstruction(
    { "Asr" },
    shiftInstruction(CPU::barrelShifterArithmeticRight)
)
val thumbRor = ThumbInstruction(
    { "Ror" },
    shiftInstruction(CPU::barrelShifterRotateRight)
)

val thumbAdc = ThumbInstruction(
    { "Adc" },
    instruction {
        perform { destination + operand + carry }
        overflow() // todo
        dumbCarry { destination.uLong + operand.uLong + carry.uLong }
    }
)

val thumbSbc = ThumbInstruction(
    { "Sbc" },
    instruction {
        perform { destination - operand - 1 + carry }
        subOverflow()
        dumbBorrow { destination.uLong - operand.uLong - 1L + carry.uLong }
    }
)

val thumbCmp = ThumbInstruction(
    { "Cmp" },
    instruction {
        performWithoutDestination { destination - operand }
        subOverflow()
        dumbBorrow { destination.uLong - operand.uLong }
    }
)

val thumbCmn = ThumbInstruction(
    { "Cmn" },
    instruction {
        performWithoutDestination { destination + operand }
        overflow()
        dumbCarry { destination.uLong + operand.uLong }
    }
)
