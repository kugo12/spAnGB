package spAnGB.ppu

import spAnGB.utils.uInt

inline fun Short.toBufferColor() = uInt or 0x40000000

inline fun Int.toCoefficient() = and(0x1F).let { if (it >= 16) 16 else it }

inline fun Short.toColor() = toInt().toColor()


inline fun Int.toColor() =
    and(0x1F).shl(27)  // R
        .or(and(0x3E0).shl(14))  // G
        .or(and(0x7C00).shl(1))  // B
        .or(0xFF)  // A

inline fun Int.transformColors(transformation: (Int) -> Int): Int =
    transformation(and(0x1F))
        .or(transformation(ushr(5).and(0x1F)).shl(5))
        .or(transformation(ushr(10).and(0x1F)).shl(10))