package spAnGB.ppu

import spAnGB.memory.RAM
import spAnGB.ppu.mmio.DisplayStat
import spAnGB.ppu.mmio.DisplayStatFlag
import spAnGB.ppu.mmio.VCount
import spAnGB.utils.KiB
import java.nio.ByteBuffer

const val HDRAW_CYCLES = 960
const val HBLANK_CYCLES = 272
const val SCANLINE_CYCLES = HDRAW_CYCLES + HBLANK_CYCLES

const val VBLANK_HEIGHT = 68
const val VDRAW_HEIGHT = 160
const val TOTAL_HEIGHT = VBLANK_HEIGHT + VDRAW_HEIGHT

class PPU(
    val framebuffer: ByteBuffer
) {
    val palette = RAM(1 * KiB)
    val vram = VRAM()
    val attributes = RAM(1 * KiB)

    val displayStat = DisplayStat()
    val vcount = VCount()

    enum class PPUState { HDraw, HBlank, VBlank }

    var state = PPUState.HDraw
    var cyclesLeft = HDRAW_CYCLES

    fun Short.toColor() = ByteArray(3).apply {
        val c = toUShort().toInt()
        set(0, c.and(0x1F).shl(3).toByte())
        set(1, c.ushr(5).and(0x1F).shl(3).toByte())
        set(2, c.ushr(10).and(0x1F).shl(3).toByte())
    }

    fun getBgColorFromPalette(color: Byte): ByteArray {
        val index = color.toUByte().toInt()*2
        val color = palette.content.getShort(index)

        return color.toColor()
    }

    fun renderBgMode4() {
        val offset = vcount.ly*240

        (offset until offset+240).forEach {
            framebuffer.put(
                it * 3,
                getBgColorFromPalette(vram.content[it])
            )
        }
    }

    fun renderBgMode3() {
        val offset = vcount.ly*480

        (offset until offset + 480 step 2).forEach {
            framebuffer.put(
                (it / 2) * 3,
                vram.content.getShort(it).toColor()
            )
        }
    }

    private fun checkVCounter() {
        displayStat[DisplayStatFlag.VCOUNTER] = vcount.ly == displayStat.lyc
    }

    fun tick() {
        if (cyclesLeft > 0) {
            cyclesLeft -= 1
            return
        }

        when (state) {
            PPUState.HDraw -> {
                renderBgMode4()

                state = PPUState.HBlank
                cyclesLeft = HBLANK_CYCLES

                displayStat[DisplayStatFlag.HBLANK] = true
            }
            PPUState.HBlank -> {
                vcount.ly += 1
                checkVCounter()

                if (vcount.ly >= VDRAW_HEIGHT) {
                    state = PPUState.VBlank
                    cyclesLeft = SCANLINE_CYCLES

                    displayStat[DisplayStatFlag.HBLANK] = false
                    displayStat[DisplayStatFlag.VBLANK] = true
                } else {
                    state = PPUState.HDraw
                    cyclesLeft = HDRAW_CYCLES
                }
            }
            PPUState.VBlank -> {
                vcount.ly += 1

                if (vcount.ly >= TOTAL_HEIGHT) {
                    state = PPUState.HDraw
                    cyclesLeft = HDRAW_CYCLES

                    displayStat[DisplayStatFlag.VBLANK] = false
                    vcount.ly = 0
                    checkVCounter()
                } else {
                    cyclesLeft = SCANLINE_CYCLES
                    checkVCounter()
                }
            }
        }
    }
}