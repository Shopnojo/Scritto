plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Secrets come from the git-ignored ".env" file at the project root (see ".env.example"),
// or from real environment variables of the same name (handy in CI); those win.
//   GEMINI_API_KEYS                                        -> BuildConfig.GEMINI_API_KEYS
//   RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD -> release signing
// Nothing here is required: without them the app still builds (AI simply shows "no API key").
val dotEnv: Map<String, String> = providers
    .fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText
    .map { text ->
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                line.substringBefore("=").trim() to
                    line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            }
    }
    .orElse(emptyMap())
    .get()

fun secret(name: String): String =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: dotEnv[name].orEmpty()

val geminiApiKeys: String = secret("GEMINI_API_KEYS").trim()

android {
    namespace = "com.internship.scritto"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.internship.scritto"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEYS", "\"$geminiApiKeys\"")
    }

    signingConfigs {
        val storePath = secret("RELEASE_STORE_FILE")

        // Only defined when a keystore is configured, so a fresh checkout still builds.
        if (storePath.isNotBlank() && file(storePath).exists()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }

            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.4")

    // Material outline icons for the compact Scritto dock
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
    // The Android SDK jar stubs org.json in local unit tests; use the real one there.
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
