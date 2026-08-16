import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "org.j96.flashairdownloader"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "org.j96.flashairdownloader"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en", "ja")
    }

    /**
     * The release signing key comes from the environment, so that CI can sign
     * with a key kept in repository secrets while a plain local build stays
     * exactly as it was: unsigned. Everything has to be there or the config is
     * left out entirely -- a half-configured key would fail the build for
     * anyone who just wants to compile.
     */
    val releaseKeystore = releaseSigningFromEnvironment(providers)
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore.storeFile)
                storePassword = releaseKeystore.storePassword
                keyAlias = releaseKeystore.keyAlias
                keyPassword = releaseKeystore.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        // targetSdk deliberately stays at the designed Android 16 even though
        // compileSdk is a platform ahead (docs/design.md 4, README).
        disable += "OldTargetApi"
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        allWarningsAsErrors = false
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.coil.compose)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
    // Provides the activity the Compose test rule launches its content in.
    debugImplementation(libs.compose.ui.test.manifest)
}

/** The release signing key, when the environment carries a complete one. */
data class ReleaseKeystore(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun releaseSigningFromEnvironment(providers: ProviderFactory): ReleaseKeystore? {
    fun environment(name: String) = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    return ReleaseKeystore(
        storeFile = environment("RELEASE_KEYSTORE_FILE") ?: return null,
        storePassword = environment("RELEASE_KEYSTORE_PASSWORD") ?: return null,
        keyAlias = environment("RELEASE_KEY_ALIAS") ?: return null,
        keyPassword = environment("RELEASE_KEY_PASSWORD") ?: return null,
    )
}
