@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.ppu.sprite

import spAnGB.ppu.TransparentPriority

class SpritePixel(
    var priority: Int = TransparentPriority,
    var isMosaic: Boolean = false,
    var isSemiTransparent: Boolean = false,
    var color: Short = 0,

    var isWindow: Boolean = false
) {
    fun clear() {
        priority = TransparentPriority
        isSemiTransparent = false
        isMosaic = false
        isWindow = false
    }

    inline fun apply(
        isTransparent: Boolean,
        isWindow: Boolean,
        priority: Int,
        isMosaic: Boolean,
        isSemiTransparent: Boolean,
        color: Short
    ) {
        if (isTransparent) {
            this.isMosaic = isMosaic
        } else if (isWindow) {
            this.isWindow = true
        } else if (priority < this.priority) {
            this.priority = priority
            this.isMosaic = isMosaic
            this.isSemiTransparent = isSemiTransparent
            this.color = color
        }
    }

    inline fun isTransparent() = priority == TransparentPriority
}

