plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
  implementation(project(":contracts"))
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test { useJUnitPlatform() }

