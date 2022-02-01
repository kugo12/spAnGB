package spAnGB.memory.mmio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import spAnGB.memory.Memory

enum class KeyInputFlags(val mask: Int) {
    A(0x1),
    B(0x2),
    Select(0x4),
    Start(0x8),
    Right(0x10),
    Left(0x20),
    Up(0x40),
    Down(0x80),
    R(0x10),
    L(0x20)
}

val keyMap = listOf(
    Input.Keys.Z,
    Input.Keys.X,
    Input.Keys.S,
    Input.Keys.A,
    Input.Keys.RIGHT,
    Input.Keys.LEFT,
    Input.Keys.UP,
    Input.Keys.DOWN,
    Input.Keys.W,
    Input.Keys.Q
)

class KeyInput : Memory {  // read only
    var value = 0x3FF

    fun poll() {
        value = 0
        keyMap.forEachIndexed { index, key ->
            if (!Gdx.input.isKeyPressed(key))
                value = value or (1 shl index)
        }
    }

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short = value.toShort()

    override fun read32(address: Int): Int = value

    override fun write8(address: Int, value: Byte) {}
    override fun write16(address: Int, value: Short) {}
    override fun write32(address: Int, value: Int) {}
}