@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.utils

inline val Int.hex: String get() = Integer.toHexString(this)

inline infix fun Int.bit(bit: Int): Boolean = and((1 shl bit)) != 0

inline fun Boolean.toInt(): Int = if (this) 1 else 0

inline fun IntArray.swapWith(arr: IntArray, destinationOffset: Int, startIndex: Int, endIndex: Int) {
    (startIndex .. endIndex).forEach { index ->
        this[index] = arr[index + destinationOffset].also { arr[index + destinationOffset] = this[index] }
    }
}