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


inline fun read8From16(address: Int, value: Int): Byte =
    if (address bit 0) value.ushr(8).toByte() else value.toByte()

inline fun write8to16(address: Int, current: Int, value: Byte): Int =
    if (address bit 0) (current and 0xFF) or (value.uInt shl 8)
    else (current and 0xFF00) or (value.uInt)

inline fun write8to32(address: Int, current: Int, value: Byte): Int =
    when (address and 3) {
        0 -> (current and 0xFF.inv()) or value.uInt
        1 -> (current and 0xFF00.inv()) or (value.uInt.shl(8))
        2 -> (current and 0xFF0000.inv()) or (value.uInt.shl(16))
        3 -> (current and 0xFFFFFF) or (value.uInt.shl(24))
        else -> current
    }