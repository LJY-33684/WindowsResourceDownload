plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    kotlin("plugin.compose") version "2.1.20"
    id("org.jetbrains.compose") version "1.9.0"
}

group = "link.mczihan.androidResourceDownload"
version = "2.3.9"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0-alpha04")
    implementation(compose.materialIconsExtended)

    // Force skiko runtime to match skiko-awt version (fixes UnsatisfiedLinkError)
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.9")



    // Lifecycle (ViewModel)
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // XML parsing
    implementation("net.sf.kxml:kxml2:2.3.0")
    implementation("xmlpull:xmlpull:1.1.3.1")

    // JNA for Windows API (title bar dark mode)
    implementation("net.java.dev.jna:jna:5.14.0")

    // Material Color Utilities (HCT color science for theme seed colors)
    implementation("com.materialkolor:material-color-utilities:1.5.0")
    // JavaFX WebView for in-app GitHub OAuth login (captures deep-link navigation)
    implementation("org.openjfx:javafx-base:21.0.11:win")
    implementation("org.openjfx:javafx-graphics:21.0.11:win")
    implementation("org.openjfx:javafx-controls:21.0.11:win")
    implementation("org.openjfx:javafx-web:21.0.11:win")
    implementation("org.openjfx:javafx-swing:21.0.11:win")
}

compose.desktop {
    application {
        mainClass = "link.mczihan.androidResourceDownload.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "WindowsResourceDownload"
            packageVersion = "2.3.9"
            modules("jdk.httpserver", "javafx.base", "javafx.graphics", "javafx.controls", "javafx.web", "javafx.swing")
            windows {
                menu = true
                shortcut = true
                iconFile.set(project.file("app_icon.ico"))
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}
