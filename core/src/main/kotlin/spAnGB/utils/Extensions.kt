@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.utils

inline val Int.hex: String get() = Integer.toHexString(this)

inline infix fun Int.bit(bit: Int): Boolean = and((1 shl bit)) != 0
inline infix fun Long.bit(bit: Int): Boolean = and((1L shl bit)) != 0L

inline fun Boolean.toInt(): Int = if (this) 1 else 0

