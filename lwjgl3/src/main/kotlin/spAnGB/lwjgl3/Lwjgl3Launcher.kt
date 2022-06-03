@file:JvmName("Lwjgl3Launcher")

package spAnGB.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import spAnGB.Application

/** Launches the desktop (LWJGL3) application. */
fun main() {
    Lwjgl3Application(Application(), Lwjgl3ApplicationConfiguration().apply {
        setTitle("spAnGB")
        setWindowedMode(1280, 720)
        setWindowIcon(*(
                arrayOf(128, 64, 32, 16)
                    .map { "libgdx$it.png" }
                    .toTypedArray()
                )
        )
    })
}
