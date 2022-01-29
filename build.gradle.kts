import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val gwtPluginVersion: String by project

repositories {
	mavenCentral()
}

plugins {
	idea
	`java-library`
	kotlin("jvm") version "1.6.10"
}

subprojects {
	apply(plugin = "org.jetbrains.kotlin.jvm")


	version = "0.0.1"
	extra.set("appName", "spAnGB")

	repositories {
		mavenCentral()
	}

	tasks {
		withType<KotlinCompile> {
			kotlinOptions {
				freeCompilerArgs = listOf("-Xjsr305=strict")
				jvmTarget = "1.8"
			}
		}

		withType<Test> {
			useJUnitPlatform()
		}
	}

	java.sourceCompatibility = JavaVersion.VERSION_1_8
}
