package spAnGB.cpu

import spAnGB.utils.hex

data class CPUInstruction(
    val type: CPUState,
    val description: CPU.(Int) -> String,
    val execute: CPU.(Int) -> Unit
)

fun ThumbInstruction(description: CPU.(Int) -> String, execute: CPU.(Int) -> Unit) = CPUInstruction(
    CPUState.THUMB,
    description,
    execute
)

fun ARMInstruction(description: CPU.(Int) -> String, execute: CPU.(Int) -> Unit) = CPUInstruction(
    CPUState.ARM,
    description,
    execute
)

fun undefinedThumbInstruction(op: Int) = ThumbInstruction(
    { "Undefined THUMB ${it.hex}" },
    { TODO("Undefined THUMB instruction ${op.hex}/${it.hex} @ ${pc.hex}") }
)

fun undefinedArmInstruction(op: Int) = ARMInstruction(
    { "Undefined ARM ${it.hex}" },
    { TODO("Undefined ARM instruction ${op.hex}/${it.hex} @ ${pc.hex}") }
)
