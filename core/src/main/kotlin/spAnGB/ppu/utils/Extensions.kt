package spAnGB.ppu

import spAnGB.ppu.mmio.bg.BackgroundCoordinate
import spAnGB.utils.uInt

inline fun Short.toBufferColor() = uInt or 0x40000000

inline fun Int.toCoefficient() = and(0x1F).let { if (it >= 16) 16 else it }

inline fun Short.toColor() = toInt().toColor()

inline fun Array<BackgroundCoordinate>.copyToInternal() = forEach { it.internal = it.value }
inline fun Array<BackgroundCoordinate>.lock() = forEach { it.lock = true }
inline fun Array<BackgroundCoordinate>.unlock() = forEach { it.lock = false }

inline fun Int.toColor() =
    and(0x1F).shl(27)  // R
        .or(and(0x3E0).shl(14))  // G
        .or(and(0x7C00).shl(1))  // B
        .or(0xFF)  // A

inline fun Int.transformColors(transformation: (Int) -> Int): Int =
    transformation(and(0x1F))
        .or(transformation(ushr(5).and(0x1F)).shl(5))
        .or(transformation(ushr(10).and(0x1F)).shl(10))

inline fun transformColors(firstTarget: Int, secondTarget: Int, transformation: (Int, Int) -> Int) =
    transformation(firstTarget and 0x1F, secondTarget and 0x1F)
        .or(transformation(firstTarget ushr 5 and 0x1F, secondTarget ushr 5 and 0x1F).shl(5))
        .or(transformation(firstTarget ushr 10 and 0x1F, secondTarget ushr 5 and 0x1F).shl(10))
