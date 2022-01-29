package spAnGB.debugger

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.kotcrab.vis.ui.util.TableUtils
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.scene2d.KTable
import ktx.scene2d.vis.visLabel
import spAnGB.cpu.CPU
import spAnGB.utils.hex

class CPURegistersWindow(val cpu: CPU) : VisWindow("CPU registers"), KTable {
    private val registers = mutableListOf<Label>()

    init {
        TableUtils.setSpacingDefaults(this)

        (0..15).forEach {
            row()
            visLabel("R$it")
            registers.add(visLabel("00000000"))
        }

        pack()

        x = 800f
    }

    override fun act(delta: Float) {
        super.act(delta)

        registers.forEachIndexed { i, label ->
            label.setText(cpu.registers[i].hex)
        }
    }
}