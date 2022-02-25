val gdxVersion: String by project
val ktxVersion: String by project
val kotlinVersion: String by project

dependencies {
	api("com.badlogicgames.gdx:gdx:$gdxVersion")
	api("io.github.libktx:ktx-app:$ktxVersion")
	api("io.github.libktx:ktx-assets:$ktxVersion")
	api("io.github.libktx:ktx-graphics:$ktxVersion")
	api("io.github.libktx:ktx-vis:$ktxVersion")
	api("io.github.libktx:ktx-scene2d:$ktxVersion")
	api("io.github.libktx:ktx-actors:$ktxVersion")
	api("com.kotcrab.vis:vis-ui:1.5.0") // TODO

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")

	testImplementation(kotlin("test"))
}
