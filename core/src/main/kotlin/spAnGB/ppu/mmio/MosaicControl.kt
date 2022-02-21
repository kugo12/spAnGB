package spAnGB.ppu.mmio

import spAnGB.memory.mmio.SimpleMMIO

class MosaicControl: SimpleMMIO() {
    inline val backgroundHorizontal get() = value and 0xF
    inline val backgroundVertical get() = value.ushr(4).and(0xF)
    inline val objectHorizontal get() = value.ushr(8).and(0xF)
    inline val objectVertical get() = value.ushr(12).and(0xF)

    var objY = 0
    var objX = 0
    var bgY = 0
    var bgX = 0

    fun advanceCounters() {
        if (++objY > objectVertical) objY = 0
        if (++bgY > backgroundVertical) bgY = 0
        bgX = 0
        objX = 0
    }

    fun advanceObjX() {
        if (++objX > objectHorizontal) objX = 0
    }

    fun advanceBgX() {
        if (++bgX > backgroundHorizontal) bgX = 0
    }
    fun resetCounters() {
        objY = 0
        bgY = 0
    }
}