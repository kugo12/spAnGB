package spAnGB.debugger

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.kotcrab.vis.ui.util.TableUtils
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.scene2d.KTable
import ktx.scene2d.vis.visLabel
import spAnGB.cpu.CPU
import spAnGB.cpu.CPUFlag

class CPUFlagsWindow(val cpu: CPU) : VisWindow("CPU Flags"), KTable {
    val flags = CPUFlag.values()
    val labels = mutableListOf<Label>()

    init {
        TableUtils.setSpacingDefaults(this)
        columnDefaults(0).left()

        this@CPUFlagsWindow.flags.forEach {
            visLabel(it.description)
        }
        row()
        this@CPUFlagsWindow.flags.mapTo(this@CPUFlagsWindow.labels) {
            visLabel("false")
        }

        pack()

        x = 1000f
    }

    override fun act(delta: Float) {
        super.act(delta)

        flags.forEachIndexed { index, flag ->
            labels[index].setText(cpu[flag].toString())
        }
    }
}