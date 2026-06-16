plugins {
    kotlin("jvm") version "1.9.24"
    id("com.android.library") version "8.7.3" apply false
    kotlin("android") version "1.9.24" apply false
    application
}

group = "io.freesql"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("freesql.MainKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
