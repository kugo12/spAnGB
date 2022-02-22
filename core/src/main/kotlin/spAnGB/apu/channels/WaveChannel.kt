package spAnGB.apu.channels

import spAnGB.Scheduler
import spAnGB.SchedulerTask
import spAnGB.apu.mmio.FrequencyControl
import spAnGB.apu.mmio.WaveLengthVolume
import spAnGB.apu.mmio.WaveRAM
import spAnGB.apu.mmio.WaveRamSelect
import spAnGB.cpu.CLOCK_SPEED
import spAnGB.utils.uInt

const val GB_CLOCK_SPEED = 4194304

class WaveChannel : Channel, WithFrequency, WithLength {
    val select = WaveRamSelect()
    val volume = WaveLengthVolume()
    val control = FrequencyControl(::onReset)
    val ram = WaveRAM(select)

    var currentSample = 0
    var isEnabled = false

    override fun getSample(): Int {
        if (!select.isEnabled) return 0

        val sample = ram.memory[currentSample ushr 1].let {
            if (currentSample and 1 == 0)
                it.toInt().ushr(4).and(0xF)
            else
                it.toInt() and 0xF
        }

        return sample ushr volume.volume
    }

    override fun onReset() {
        isEnabled = true
        currentSample = 0
    }

    override fun stepLength() {
        if (volume.length > 0) {
            volume.length -= 1
        } else if (control.shouldStopAfterExpiring) {
            select.isEnabled = false
        }
    }

    override fun step(cycles: Long) {
        if (control.frequency == 0) return

        val mask = select.counterMask

        val x = cycles / control.cyclesPerIncrement
        currentSample = (currentSample + x.toInt()) and mask
    }
}