package spAnGB.apu.channels

sealed interface Channel {
    fun getSample(): Int
    fun onReset()
}

sealed interface WithLength {
    fun stepLength()
}

sealed interface WithEnvelope {
    fun stepEnvelope()
}

sealed interface WithSweep {
    fun stepSweep()
}

sealed interface WithFrequency {
    fun step(cycles: Long)
}