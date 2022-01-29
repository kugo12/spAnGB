@file:Suppress("NOTHING_TO_INLINE")

package spAnGB.cpu

@JvmInline
value class Pipeline(
    val content: IntArray = IntArray(3)
) {
    inline val head: Int get() = content[2]

    inline fun flush(): Unit = content.fill(0)

    inline fun thumbFill(cpu: CPU) {
        content[2] = cpu.bus.read16(cpu.pc).toUShort().toInt()
        cpu.pc += 2
        content[1] = cpu.bus.read16(cpu.pc).toUShort().toInt()
        cpu.pc += 2
        content[0] = cpu.bus.read16(cpu.pc).toUShort().toInt()
    }

    inline fun thumbRefill(cpu: CPU) {
        cpu.pc = cpu.pc and (1.inv())
        content[1] = cpu.bus.read16(cpu.pc).toUShort().toInt()
        cpu.pc += 2
        content[0] = cpu.bus.read16(cpu.pc).toUShort().toInt()
    }

    inline fun thumbStep(cpu: CPU) {
        content[2] = content[1]
        content[1] = content[0]
        cpu.pc += 2
        content[0] = cpu.bus.read16(cpu.pc).toUShort().toInt()
    }

    inline fun armFill(cpu: CPU) {
        content[2] = cpu.bus.read32(cpu.pc)
        cpu.pc += 4
        content[1] = cpu.bus.read32(cpu.pc)
        cpu.pc += 4
        content[0] = cpu.bus.read32(cpu.pc)
    }

    inline fun armRefill(cpu: CPU) {
        cpu.pc = cpu.pc and (3.inv())
        content[1] = cpu.bus.read32(cpu.pc)
        cpu.pc += 4
        content[0] = cpu.bus.read32(cpu.pc)
    }

    inline fun armStep(cpu: CPU) {
        content[2] = content[1]
        content[1] = content[0]
        cpu.pc += 4
        content[0] = cpu.bus.read32(cpu.pc)
    }
}