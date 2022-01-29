@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu

enum class CPUState {
    ARM, THUMB;

    companion object {
        val values = values()

        inline fun from(ordinal: Int): CPUState = values[ordinal]
    }
}