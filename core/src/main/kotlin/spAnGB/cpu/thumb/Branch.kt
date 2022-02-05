package spAnGB.cpu.thumb

import spAnGB.cpu.CPUFlag
import spAnGB.cpu.CPUMode
import spAnGB.cpu.ThumbInstruction
import spAnGB.utils.bit
import spAnGB.utils.hex

val thumbConditionalBranch = ThumbInstruction(
    { "conditional branch" },
    { instr ->
        if (checkCondition(instr.ushr(8).and(0xF))) {
            val offset = instr.and(0xFF).toByte().toInt().shl(1)
            pc += offset
            thumbRefill()
        }
    }
)

val thumbBranch = ThumbInstruction(
    { "b" },
    { instr ->
        val offset = instr.and(0x7FF).shl(5).toShort().toInt().shr(4)
        pc += offset

        thumbRefill()
    }
)

val thumbLongBranchLink = ThumbInstruction(
    { "long branch" },
    { instr ->  // TODO
        if (instr bit 11) {  // low
            val offset = instr.and(0x7FF).shl(1)
            val to = lr + offset

            lr = pc.minus(2).or(1)
            pc = to

            thumbRefill()
        } else {  // high
            val offset = instr.and(0x7FF).shl(5).toShort().toInt()
            lr = pc + offset.shl(7)
        }
    }
)

val thumbSwi = ThumbInstruction(
    { "Swi" },
    { instr ->
        val cpsr = cpsr

        setCPUMode(CPUMode.Supervisor)

        lr = pc - 2
        pc = 0x08
        spsr = cpsr

        this[CPUFlag.I] = true
        changeState(0)
    }
)
