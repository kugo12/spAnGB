package spAnGB.cpu.arm

import spAnGB.cpu.CPUInstruction
import spAnGB.cpu.undefinedArmInstruction
import spAnGB.utils.bit


fun ARMOpFactory(op: Int): CPUInstruction {
    // 00001111111100000000000011110000  0x0FF000F0
    if (op and 0xF00 == 0xF00) {
        return armSwi
    }

    if (op == 0x121) {
        return armBx
    }

    if (op and 0xE00 == 0xA00) {
        return when (op bit 8) {
            true -> armBl
            false -> armB
        }
    }

    if (op and 0xFCF == 0x009) {
        return armMulMla
    }

    if (op and 0xF8F == 0x089) {
        return if (op bit 6) {
            armSmullSmlal
        } else {
            armUmullUmlal
        }
    }

    if (op and 0xFBF == 0x109) {
        return armSwp
    }

    if (op and 0xE10 == 0x810) {
        return armLdm
    }

    if (op and 0xE10 == 0x800) {
        return armStm
    }

    if (op and 0xE09 == 0x009) {
        return if (op bit 4) { // load
            armLdrhsb
        } else { // store
            armStrhsb
        }
    }

    if (op and 0xC10 == 0x410) {
        return armLdr
    }

    if (op and 0xC10 == 0x400) {
        return armStr
    }


    // DATA PROCESSING (btw it should be at the bottom)
    if (op and 0xFBF == 0x100) {
        return armMrs
    }

    if ((op and 0xFBF == 0x120) || (op and 0xFB0 == 0x320)) {
        return armMsr
    }

    if (op and 0xDE0 == 0x000) {
        return armAnd
    }

    if (op and 0xDE0 == 0x020) {
        return armEor
    }

    if (op and 0xDE0 == 0x040) {
        return armSub
    }

    if (op and 0xDE0 == 0x060) {
        return armRsb
    }

    if (op and 0xDE0 == 0x080) {
        return armAdd
    }

    if (op and 0xDE0 == 0x0A0) {
        return armAdc
    }

    if (op and 0xDE0 == 0x0C0) {
        return armSbc
    }

    if (op and 0xDE0 == 0x0E0) {
        return armRsc
    }

    if (op and 0xDE0 == 0x100) {
        return armTst
    }

    if (op and 0xDE0 == 0x120) {
        return armTeq
    }

    if (op and 0xDE0 == 0x140) {
        return armCmp
    }

    if (op and 0xDE0 == 0x160) {
        return armCmn
    }

    if (op and 0xDE0 == 0x180) {
        return armOrr
    }

    if (op and 0xDE0 == 0x1A0) {
        return armMov
    }

    if (op and 0xDE0 == 0x1C0) {
        return armBic
    }

    if (op and 0xDE0 == 0x1E0) {
        return armMvn
    }


    return undefinedArmInstruction(op)
} 
