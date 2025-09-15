
plugins {
  id("com.android.application") version "8.5.0" apply false
  id("com.android.library") version "8.5.0" apply false
  kotlin("android") version "1.9.24" apply false
  kotlin("jvm") version "1.9.24" apply false
  id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
  id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
}

// Repositories are managed in settings.gradle.kts (FAIL_ON_PROJECT_REPOS)

subprojects {
  // Ensure Java toolchain uses JDK 17 (with auto-provision via Foojay plugin)
  plugins.withType<JavaPlugin> {
    the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(17))
  }
  // Apply static analysis to library modules only (skip :app initially)
  if (name != "app") {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.findByName("detekt")?.let {
      (it as io.gitlab.arturbosch.detekt.extensions.DetektExtension).apply {
        buildUponDefaultConfig = true
        config = files(rootProject.file("detekt.yml"))
      }
    }

    // Ktlint defaults; treat as Android where applicable
    extensions.findByName("ktlint")?.let {
      (it as org.jlleitschuh.gradle.ktlint.KtlintExtension).apply {
        android.set(true)
      }
    }
  }

  // Use JUnit 5 across JVM tests
  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }
}
