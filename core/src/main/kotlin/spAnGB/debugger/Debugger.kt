package spAnGB.debugger

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import spAnGB.spAnGB

// TODO: refactoring

class Debugger(
    val emulator: spAnGB,
    stage: Stage
) : Actor() {
    init {
        val instr = CPUInstructionWindow(emulator.cpu)

        stage.addActor(CPURegistersWindow(emulator.cpu))
        stage.addActor(CPUFlagsWindow(emulator.cpu))
        stage.addActor(instr)
        stage.addActor(EmulatorStepperWindow(emulator, instr::addToHistory))
    }
}

