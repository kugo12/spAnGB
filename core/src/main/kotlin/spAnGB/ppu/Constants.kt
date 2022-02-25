package spAnGB.ppu

const val Tile4BitRow = 4
const val Tile4BitSize = 8 * Tile4BitRow

const val Tile8BitRow = 8
const val Tile8BitSize = 8 * Tile8BitRow

const val TwoDimensionalTileRow = 32

const val ScreenWidth = 240
const val ScreenHeight = 160

const val SpriteTileOffset = 0x10000
const val SpritePaletteOffset = 0x100

const val TransparentPriority = 4

const val HDRAW_CYCLES = 1006L
const val HBLANK_CYCLES = 226L

const val VBLANK_HEIGHT = 68
const val VDRAW_HEIGHT = ScreenHeight
const val TOTAL_HEIGHT = VBLANK_HEIGHT + VDRAW_HEIGHT

const val SpecialEffectsLut = 5

object PixelType {
    const val Sprite = 4
    const val Backdrop = 5
}

// WxH, [shape][size + 4*isDoubleSize]
val SpriteSizes: Array<Array<IntArray>> = arrayOf(
    arrayOf(  // square
        intArrayOf(8, 8),
        intArrayOf(16, 16),
        intArrayOf(32, 32),
        intArrayOf(64, 64),

        intArrayOf(16, 16),  // double size
        intArrayOf(32, 32),
        intArrayOf(64, 64),
        intArrayOf(128, 128)
    ),
    arrayOf(  // horizontal
        intArrayOf(16, 8),
        intArrayOf(32, 8),
        intArrayOf(32, 16),
        intArrayOf(64, 32),

        intArrayOf(32, 16),  // double size
        intArrayOf(64, 16),
        intArrayOf(64, 32),
        intArrayOf(128, 64)
    ),
    arrayOf(  // vertical
        intArrayOf(8, 16),
        intArrayOf(8, 32),
        intArrayOf(16, 32),
        intArrayOf(32, 64),

        intArrayOf(16, 32),  // double size
        intArrayOf(16, 64),
        intArrayOf(32, 64),
        intArrayOf(64, 128)
    )
)

// WxH
val TextBackgroundSizes = arrayOf(
    intArrayOf(256, 256),
    intArrayOf(512, 256),
    intArrayOf(256, 512),
    intArrayOf(512, 512),
)

val AffineBackgroundSizes = arrayOf(
    intArrayOf(128, 128),
    intArrayOf(256, 256),
    intArrayOf(512, 512),
    intArrayOf(1024, 1024),
)