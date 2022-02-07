package spAnGB.ppu.bg

import spAnGB.ppu.PPU

fun PPU.renderBgMode1() {
    if (displayControl.isBg0) renderBgText(0)
    if (displayControl.isBg1) renderBgText(1)
    if (displayControl.isBg2) TODO()
}