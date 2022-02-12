package spAnGB

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisWindow
import ktx.actors.stage
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.graphics.use
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.vis.KVisWindow
import ktx.scene2d.vis.multiSplitPane
import spAnGB.debugger.Debugger
import java.nio.ByteBuffer

class MainClasss : KtxGame<KtxScreen>() {
    override fun create() {
        VisUI.load()
        Scene2DSkin.defaultSkin = VisUI.getSkin()

        addScreen(FirstScreen())
        setScreen<FirstScreen>()
    }
}

class FirstScreen : KtxScreen {
    private val fb = BasicFramebufferActor()
    private val emulator = spAnGB(fb.framebuffer, fb::blitFramebuffer)

    val stage = stage().apply {
        Gdx.input.inputProcessor = this
        addActor(fb)
    }
//    private val debugger = Debugger(emulator, stage)

    override fun render(delta: Float) {
        clearScreen(0f, 0f, .25f)
        stage.draw()
        emulator.bus.mmio.keyInput.poll()
        (0..300000).forEach { emulator.tick() }
        stage.act()
    }

    override fun dispose() {
        stage.disposeSafely()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.setScreenSize(width, height)
    }
}

class BasicFramebufferActor: Actor() {
    private val pixmap = Pixmap(240, 160, Pixmap.Format.RGBA8888)
    private val texture = Texture(pixmap)

    val framebuffer: ByteBuffer = pixmap.pixels

    fun blitFramebuffer() {
        texture.draw(pixmap, 0, 0)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.draw(texture, 0f, 0f, 1280f, 720f)
    }
}
