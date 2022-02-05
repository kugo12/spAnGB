package spAnGB.debugger

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.kotcrab.vis.ui.util.TableUtils
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.actors.onChange
import ktx.scene2d.KCheckBox
import ktx.scene2d.KTable
import ktx.scene2d.checkBox
import ktx.scene2d.vis.*
import spAnGB.cpu.CPU
import spAnGB.cpu.CPUState
import spAnGB.cpu.arm.ARMOpFactory
import spAnGB.cpu.thumb.ThumbOpFactory
import spAnGB.utils.hex

class CPUInstructionWindow(val cpu: CPU) : VisWindow("CPU Instructions"), KTable {
    val pipeline: List<Label>
    val addresses: List<Label>

    val armLut = List(4096, ::ARMOpFactory)
    val thumbLut = List(1024, ::ThumbOpFactory)

    val instructionHistory: KVisTable

    fun getArm(op: Int) = armLut[((op and 0xF0) ushr 4) or ((op ushr 16) and 0xFF0)]
    fun getThumb(op: Int) = thumbLut[(op ushr 6) and 0x3FF]

    init {
        TableUtils.setSpacingDefaults(this)
        top()

        val pipelineCheck: KCheckBox
        val instructionHistoryCheck: KCheckBox
        visTable {
            pipelineCheck = checkBox("Pipeline")
            instructionHistoryCheck = checkBox("Instruction History")
        }
        row()

        collapsible {
            pipelineCheck.onChange {
                this@collapsible.isCollapsed = !this@collapsible.isCollapsed
            }


            visTable {
                center()
                this@CPUInstructionWindow.addresses = listOf(
                    visLabel(""),
                    visLabel(""),
                    visLabel(""),
                )
                row()

                visLabel("+2")
                visLabel("+1")
                visLabel("head")
                row()
                this@CPUInstructionWindow.pipeline =
                    listOf(
                        visLabel("aaaaaaaaaaaa"),
                        visLabel("aaaaaaaaaaaa"),
                        visLabel("aaaaaaaaaaaa")
                    )
            }.cell(true)
        }
        row()
        collapsible {
            instructionHistoryCheck.onChange {
                this@collapsible.isCollapsed = !this@collapsible.isCollapsed
            }

            visScrollPane {
                this@CPUInstructionWindow.instructionHistory = visTable {

                }
            }.cell(height = 300f)
        }

        pack()

        height = 500f
        width = 400f
        x = 1000f
        y = 200f
    }

    fun addToHistory() {
        val description = cpu.pipelineHead.let {
            (if (cpu.state == CPUState.THUMB) getThumb(it) else getArm(it))
                .description(cpu, it)
        }
        val address = cpu.pc - (if(cpu.state == CPUState.THUMB) 4 else 8)
        instructionHistory.apply {
            row()
            visLabel(address.hex + ": " + description)
        }
    }

    override fun act(delta: Float) {
        super.act(delta)

        pipeline.forEachIndexed { index, label ->
            val description = cpu.pipeline[index].let {
                (if (cpu.state == CPUState.THUMB) getThumb(it) else getArm(it))
                    .description(cpu, it)
            }
            label.setText(description)
        }

        val instructionSize = if(cpu.state == CPUState.THUMB) 2 else 4

        addresses.forEachIndexed { index, label ->
            label.setText(
                (cpu.pc - index*instructionSize).hex
            )
        }
    }
}