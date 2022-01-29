@file:JvmName("Lwjgl3Launcher")

package spAnGB.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import spAnGB.MainClasss

/** Launches the desktop (LWJGL3) application. */
fun main() {
    Lwjgl3Application(MainClasss(), Lwjgl3ApplicationConfiguration().apply {
        setTitle("spAnGB")
        setWindowedMode(1920, 1080)
        setWindowIcon(*(
                arrayOf(128, 64, 32, 16)
                    .map { "libgdx$it.png" }
                    .toTypedArray()
                )
        )
    })
}
