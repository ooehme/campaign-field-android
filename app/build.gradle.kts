import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

val localConfiguration = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun configuredProperty(name: String, fallback: String) = providers.gradleProperty(name)
    .orElse(providers.provider { localConfiguration.getProperty(name, fallback) })

fun signingProperty(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))

val appVersionCode = providers.gradleProperty("CAMPAIGN_FIELD_VERSION_CODE")
    .map { value ->
        value.toInt().also { require(it > 0) { "CAMPAIGN_FIELD_VERSION_CODE muss positiv sein." } }
    }
    .orElse(1)
val appVersionName = providers.gradleProperty("CAMPAIGN_FIELD_VERSION_NAME")
    .map { value -> value.trim().also { require(it.isNotEmpty()) { "CAMPAIGN_FIELD_VERSION_NAME fehlt." } } }
    .orElse("0.1.0")

val apiBaseUrl = configuredProperty(
    "CAMPAIGN_FIELD_API_BASE_URL",
    "https://example.invalid/api",
)
    .map { it.trim().trimEnd('/') }
val escapedApiBaseUrl = apiBaseUrl.map {
    it.replace("\\", "\\\\").replace("\"", "\\\"")
}
val sanctumClientOrigin = configuredProperty(
    "CAMPAIGN_FIELD_SANCTUM_CLIENT_ORIGIN",
    "https://example.invalid",
)
    .map { it.trim().trimEnd('/') }
val escapedSanctumClientOrigin = sanctumClientOrigin.map {
    it.replace("\\", "\\\\").replace("\"", "\\\"")
}
val mapStyleUrl = configuredProperty(
    "CAMPAIGN_FIELD_MAP_STYLE_URL",
    "https://tiles.openfreemap.org/styles/dark",
).map { it.trim() }
val escapedMapStyleUrl = mapStyleUrl.map {
    it.replace("\\", "\\\\").replace("\"", "\\\"")
}

val releaseSigningStoreFile = signingProperty("CAMPAIGN_FIELD_SIGNING_STORE_FILE")
val releaseSigningStorePassword = signingProperty("CAMPAIGN_FIELD_SIGNING_STORE_PASSWORD")
val releaseSigningKeyAlias = signingProperty("CAMPAIGN_FIELD_SIGNING_KEY_ALIAS")
val releaseSigningKeyPassword = signingProperty("CAMPAIGN_FIELD_SIGNING_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword,
).all { !it.orNull.isNullOrBlank() }

android {
    namespace = "de.oliveroehme.campaignfield"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.oliveroehme.campaignfield"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()

        buildConfigField("String", "API_BASE_URL", "\"${escapedApiBaseUrl.get()}\"")
        buildConfigField(
            "String",
            "SANCTUM_CLIENT_ORIGIN",
            "\"${escapedSanctumClientOrigin.get()}\"",
        )
        buildConfigField("String", "MAP_STYLE_URL", "\"${escapedMapStyleUrl.get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseSigningStoreFile.get())
                storePassword = releaseSigningStorePassword.get()
                keyAlias = releaseSigningKeyAlias.get()
                keyPassword = releaseSigningKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Prüft die für ein Release erforderliche Android-Signatur."
    doLast {
        check(releaseSigningConfigured) {
            "Release-Signierung fehlt. Setze CAMPAIGN_FIELD_SIGNING_STORE_FILE, " +
                "CAMPAIGN_FIELD_SIGNING_STORE_PASSWORD, CAMPAIGN_FIELD_SIGNING_KEY_ALIAS und " +
                "CAMPAIGN_FIELD_SIGNING_KEY_PASSWORD."
        }
        check(rootProject.file(releaseSigningStoreFile.get()).isFile) {
            "Der konfigurierte Release-Keystore existiert nicht."
        }
    }
}

tasks.matching {
    it.name == "packageRelease" ||
        it.name == "bundleRelease" ||
        it.name == "signReleaseBundle"
}.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.maplibre.android)

    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.jvm)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
