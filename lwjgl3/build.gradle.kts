plugins {
	application
}

val gdxVersion: String by project
val appName: String by extra


dependencies {
	implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
	implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
	implementation(project(":core"))
}

val os = System.getProperties()["os.name"] as String

application {
	mainClass.set("spAnGB.lwjgl3.Lwjgl3Launcher")
}

sourceSets {
	main {
		resources.srcDir(rootProject.file("assets").path)
	}
}

tasks {

	run {

//		workingDir = rootProject.file("assets").path
//		setIgnoreExitValue(true)
//
		if (os.contains("mac")) {
//			 Required to run LWJGL3 Java apps on MacOS
//			jvmArgs += "-XstartOnFirstThread"
		}
	}

	jar {
		archiveBaseName.set(appName)
		duplicatesStrategy = DuplicatesStrategy.EXCLUDE

		dependsOn(configurations.runtimeClasspath)
		from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

		// these "exclude" lines remove some unnecessary duplicate files in the output JAR.
		exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
		dependencies {
			exclude("META-INF/INDEX.LIST", "META-INF/maven/**")
		}

		// setting the manifest makes the JAR runnable.
		manifest {
			attributes("Main-Class" to application.mainClass)
		}

		// this last step may help on some OSes that need extra instruction to make runnable JARs.
		doLast {
			file(archiveFile).setExecutable(true, false)
		}
	}
}

// Equivalent to the jar task; here for compatibility with gdx-setup.
//task dist(dependsOn: [jar]) {
//}
