package spAnGB.apu

import com.badlogic.gdx.Gdx
import spAnGB.Scheduler
import spAnGB.apu.channels.*
import spAnGB.apu.mmio.master.MainVolumeControl
import spAnGB.apu.mmio.master.SoundBias
import spAnGB.apu.mmio.master.SoundControl
import spAnGB.apu.mmio.master.SoundMasterControl
import spAnGB.cpu.CLOCK_SPEED

const val SAMPLING_RATE = 32768
const val APU_CLOCK = (CLOCK_SPEED / SAMPLING_RATE).toLong()
const val FS_CLOCK = (CLOCK_SPEED / 512).toLong()
const val AUDIO_BUFFER_SIZE = 1024

class AudioManager {
    val audio = Gdx.audio.newAudioDevice(SAMPLING_RATE, false)
    val buffer = ShortArray(AUDIO_BUFFER_SIZE)
    var ptr = 0

    inline fun putSamples(left: Int, right: Int) {
        buffer[ptr] = left.toShort()
        buffer[ptr + 1] = right.toShort()
        ptr += 2

        if (ptr >= AUDIO_BUFFER_SIZE) {
            ptr = 0
            audio.writeSamples(buffer, 0, AUDIO_BUFFER_SIZE)
        }
    }
}

class APU(  // TODO: i'm currently making it work, but I really need to do refactoring in future
    val scheduler: Scheduler
) {
    val audio = AudioManager()

    val sweep = SweepToneChannel()
    val tone = ToneChannel()
    val wave = WaveChannel()
    val noise = NoiseChannel()
    val fifoA = FIFOChannel()
    val fifoB = FIFOChannel()

    val master = SoundMasterControl()
    val control = SoundControl()
    val volume = MainVolumeControl()
    val bias = SoundBias()

    init {
        scheduler.cyclicTask(APU_CLOCK) {
            if (master.isEnabled) tick()
        }
        scheduler.cyclicTask(FS_CLOCK) {
            tickFrameSequencer()
        }
    }

    private fun tick() {
        sweep.step(APU_CLOCK)
        tone.step(APU_CLOCK)
        wave.step(APU_CLOCK)
        noise.step(APU_CLOCK)

        var left = 0
        var right = 0

        val ch1 = sweep.getSample()
        val ch2 = tone.getSample()
        val ch3 = wave.getSample()
        val ch4 = noise.getSample()

        if (volume isLeftEnabled 0) left += ch1
        if (volume isRightEnabled 0) right += ch1
        if (volume isLeftEnabled 1) left += ch2
        if (volume isRightEnabled 1) right += ch2
        if (volume isLeftEnabled 2) left += ch3
        if (volume isRightEnabled 2) right += ch3
        if (volume isLeftEnabled 3) left += ch4
        if (volume isRightEnabled 3) right += ch4

        left *= volume.volumeLeft
        right *= volume.volumeRight

        audio.putSamples(left, right)
    }

    var fsCounter = 0
    private fun tickFrameSequencer() {
        if (fsCounter and 3 == 2) {
            sweep.stepSweep()
        }

        if (fsCounter and 1 == 0) {
            sweep.stepLength()
            tone.stepLength()
            wave.stepLength()
            noise.stepLength()
        }

        if (fsCounter == 7) {
            sweep.stepEnvelope()
            tone.stepEnvelope()
            noise.stepEnvelope()
        }

        fsCounter = (fsCounter + 1) and 7
    }
}