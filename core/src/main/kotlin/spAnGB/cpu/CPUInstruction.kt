package spAnGB.cpu

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