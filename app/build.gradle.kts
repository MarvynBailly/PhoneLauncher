import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Run a git command and return its trimmed stdout (empty string on any failure).
fun git(vararg args: String): String = try {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine(listOf("git") + args)
        standardOutput = stdout
        isIgnoreExitValue = true
    }
    stdout.toString().trim()
} catch (e: Exception) {
    ""
}

// versionCode must increase on every release so Android (and Obtainium) accepts
// the install as an upgrade rather than rejecting it as a downgrade. Derive it
// from the git commit count, which grows monotonically. release.sh makes a
// (possibly empty) release commit before building, guaranteeing a bump.
// Both values can be overridden with -PversionCode= / -PversionName=.
val computedVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
    ?: git("rev-list", "--count", "HEAD").toIntOrNull()
    ?: 1
val computedVersionName = (project.findProperty("versionName") as String?)
    ?: git("describe", "--tags", "--abbrev=0").removePrefix("v").ifBlank { "1.0" }

android {
    namespace = "com.phonelauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.phonelauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = computedVersionName
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
}
