plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "link.mczihan.androidResourceDownload"
version = "2.3.2"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Force skiko runtime to match skiko-awt version (fixes UnsatisfiedLinkError)
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.9")

    // Navigation
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha08")

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
}

compose.desktop {
    application {
        mainClass = "link.mczihan.androidResourceDownload.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "WindowsResourceDownload"
            packageVersion = "2.3.1"
            modules("jdk.httpserver")
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
