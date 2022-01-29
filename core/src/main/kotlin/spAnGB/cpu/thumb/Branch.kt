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
            pipeline.thumbRefill(this)
        }
    }
)

val thumbBranch = ThumbInstruction(
    { "b" },
    { instr ->
//    (((((instr&0x7FF) << 5) as i16 as i32) >> 4) as u32)
        val offset = instr.and(0x7FF).shl(5).toShort().toInt().shr(4)
        pc += offset

        pipeline.thumbRefill(this)
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

            pipeline.thumbRefill(this)
        } else {  // high
            val offset = instr.and(0x7FF).shl(5).toShort().toInt()
            lr = pc + offset.shl(7)
        }
    }
)

val thumbSwi = ThumbInstruction(
    { "Swi" },
    { instr ->
        println("[CPU] THUMB SWI: ${instr.hex}")

        val cpsr = cpsr

        setCPUMode(CPUMode.Supervisor)

        lr = pc - 2
        pc = 0x08
        spsr = cpsr

        this[CPUFlag.I] = true
        changeState(0)
    }
)
