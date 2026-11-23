import java.util.Properties

val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}
val hasLocalSigning = signingPropertiesFile.isFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.peyi.sonypodsbattery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.peyi.sonypodsbattery"
        minSdk = 29
        targetSdk = 35
        versionCode = 97
        versionName = "2.29.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    signingConfigs {
        if (hasLocalSigning) {
            create("localStable") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasLocalSigning) {
                signingConfig = signingConfigs.getByName("localStable")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasLocalSigning) {
                signingConfig = signingConfigs.getByName("localStable")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Serves the offline three.js Home hero (assets/hero3d) to a WebView over a virtual https origin.
    implementation("androidx.webkit:webkit:1.12.1")
    // Optional: lets SunPods do the pairing steps for LE Audio that need adb-level access.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

// The changelog ships inside the app, so "What's New" works offline and after any kind of update.
val copyChangelog by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/changelog"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/changelog").get().asFile)
tasks.named("preBuild") { dependsOn(copyChangelog) }
