package spAnGB.cpu.thumb

import spAnGB.cpu.CPUInstruction
import spAnGB.cpu.undefinedThumbInstruction

fun ThumbOpFactory(op: Int): CPUInstruction {
    if (op and 0x3FC == 0x37C) {
        return thumbSwi
    }

    if (op and 0x3D8 == 0x2D0) {
        return when (op and 0x020 != 0) {
            true -> thumbPop
            false -> thumbPush
        }
    }

    if (op and 0x3C0 == 0x300) {
        return when (op and 0x020 != 0) {
            true -> thumbLdmia
            false -> thumbStmia
        }
    }

    if (op and 0x3C0 == 0x340) {
        return thumbConditionalBranch
    }

    if (op and 0x3E0 == 0x380) {
        return thumbBranch
    }

    if (op and 0x3C0 == 0x3C0) {
        return thumbLongBranchLink
    }

    if (op and 0x3F0 == 0x100) {
        return listOf(
            thumbAnd,
            thumbEor,
            thumbLsl,
            thumbLsr,
            thumbAsr,
            thumbAdc,
            thumbSbc,
            thumbRor,
            thumbTst,
            thumbNeg,
            thumbCmp,
            thumbCmn,
            thumbOrr,
            thumbMul,
            thumbBic,
            thumbMvn
        )[op and 0xF]
    }

    if (op and 0x3F0 == 0x110) {
        return when ((op ushr 2) and 0x3) {
            0 -> thumbHiLoAdd
            1 -> thumbHiLoCmp
            2 -> thumbHiLoMov
            3 -> thumbHiLoBx
            else -> throw IllegalStateException()
        }
    }

    if (op and 0x3C8 == 0x148) {
        return when ((op ushr 4) and 0x3) {
            0 -> thumbStrh
            1 -> thumbLdsb
            2 -> thumbLdrh
            3 -> thumbLdsh
            else -> throw IllegalStateException()
        }
    }

    if (op and 0x3E0 == 0x060) {
        return thumbAddSub
    }

    if (op and 0x380 == 0x180) {
        return thumbLdrStrImmOff
    }

    if (op and 0x3C0 == 0x200) {
        return thumbLdrhStrhImmOff
    }

    if (op and 0x3E0 == 0x120) {
        return thumbLdrPcrelImm
    }

    if (op and 0x3C8 == 0x140) {
        return thumbLdrStrRegOff
    }

    if (op and 0x3C0 == 0x240) {
        return thumbLdrStrSpRel
    }

    if (op and 0x3FC == 0x2C0) {
        return thumbSpOff
    }

    if (op and 0x3C0 == 0x280) {
        return thumbLdPcSp
    }

    if (op and 0x380 == 0x080) {
        return when ((op ushr 5) and 0x3) {
            0 -> thumbMovImm
            1 -> thumbCmpImm
            2 -> thumbAddImm
            3 -> thumbSubImm
            else -> throw IllegalStateException()
        }
    }

    if (op and 0x380 == 0x000) {
        return thumbMovs
    }

    return undefinedThumbInstruction(op)
}