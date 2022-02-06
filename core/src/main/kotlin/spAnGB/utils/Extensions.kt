@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.utils

import kotlin.experimental.and

inline val Int.hex: String get() = Integer.toHexString(this)

inline infix fun Int.bit(bit: Int): Boolean = and((1 shl bit)) != 0
inline infix fun Long.bit(bit: Int): Boolean = and((1L shl bit)) != 0L

inline fun Boolean.toInt(): Int = if (this) 1 else 0

inline val Byte.uShort get() = toShort() and 0xFF
inline val Byte.uInt get() = toInt() and 0xFF
inline val Byte.uLong get() = toLong() and 0xFF

inline val Short.uInt get() = toInt() and 0xFFFF
inline val Short.uLong get() = toLong() and 0xFFFF

inline val Int.uLong get() = toLong() and 0xFFFFFFFF

inline fun Short.toColor() = toInt().let {
    it.and(0x1F).shl(27)  // R
        .or(it.and(0x3E0).shl(14))  // G
        .or(it.and(0x7C00).shl(1))  // B
        .or(0xFF)  // A
}