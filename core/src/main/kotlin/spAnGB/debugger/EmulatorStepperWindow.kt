package spAnGB.debugger

import com.kotcrab.vis.ui.widget.VisWindow
import ktx.actors.onClick
import ktx.scene2d.KTable
import ktx.scene2d.button
import ktx.scene2d.textField
import ktx.scene2d.vis.visLabel
import spAnGB.cpu.CPUState
import spAnGB.spAnGB
import spAnGB.utils.hex

class EmulatorStepperWindow(
    val emulator: spAnGB,
    val tickCallback: () -> Unit
) : VisWindow("Stepper"), KTable {
    init {
        button {
            visLabel("Step")
            onClick {
                this@EmulatorStepperWindow.tickCallback()
                this@EmulatorStepperWindow.emulator.tick()
            }
        }
        button {
            visLabel("Step 10_000")
            onClick {
//                this@EmulatorStepperWindow.tickCallback()
                try {
                    (0 .. 100000).forEach {
                        this@EmulatorStepperWindow.emulator.tick()
                    }
                } catch (t: Throwable) {
                    println(t.toString())
                    println(this@EmulatorStepperWindow.emulator.cpu.pc.hex)
                }
            }
        }
        row()
        val text = textField()

        button {
            visLabel("skip to")

            onClick {
                val address = text.text.toInt(16)

                val cpu = this@EmulatorStepperWindow.emulator.cpu
                while (true) {
                    val adjustedPC = cpu.pc - (if (cpu.state == CPUState.THUMB) 4 else 8)
                    if (address == adjustedPC) break

                    this@EmulatorStepperWindow.emulator.tick()
                }
            }
        }
        pack()

        x = 400f
    }
}