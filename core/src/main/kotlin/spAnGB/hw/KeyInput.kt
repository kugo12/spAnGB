package spAnGB.hw

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import spAnGB.memory.Memory
import spAnGB.memory.mmio.Interrupt
import spAnGB.memory.mmio.InterruptRequest
import spAnGB.memory.mmio.SimpleMMIO
import spAnGB.utils.bit
import spAnGB.utils.uInt

const val InputMask = 0x3FF

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

class KeyInput(
    ir: InterruptRequest
) : Memory {  // read only
    var value = InputMask
    val irqControl = KeyInterruptControl(ir, this)

    fun poll() {
        value = 0
        keyMap.forEachIndexed { index, key ->
            if (!Gdx.input.isKeyPressed(key))
                value = value or (1 shl index)
        }
        irqControl.process()
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

class KeyInterruptControl(
    val ir: InterruptRequest,
    val input: KeyInput
) : Memory {
    var value: Int = 0

    override fun read8(address: Int): Byte {
        TODO("Not yet implemented")
    }

    override fun read16(address: Int): Short = value.toShort()

    override fun write8(address: Int, value: Byte) {
        if (address bit 0) {  // high
            this.value = (this.value and 0xFF00.inv()) or (value.uInt.shl(8))
        } else {
            this.value = (this.value and 0xFF.inv()) or (value.uInt)
        }
        process()
    }

    override fun write16(address: Int, value: Short) {
        this.value = value.uInt
        process()
    }

    override fun read32(address: Int): Int = 0
    override fun write32(address: Int, value: Int) {}

    inline val isEnabled get() = value bit 14
    inline val isAnd get() = value bit 15
    inline val maskedValue get() = value and InputMask

    fun process() {
        if (!isEnabled) return
        if (ir[Interrupt.Keypad]) return

        ir[Interrupt.Keypad] = if (isAnd) {
            input.value.and(maskedValue) == 0
        } else {
            input.value.and(maskedValue) != maskedValue
        }
    }
}