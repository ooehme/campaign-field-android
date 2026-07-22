plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val apiBaseUrl = providers.gradleProperty("CAMPAIGN_FIELD_API_BASE_URL")
    .orElse("https://example.invalid/api")
    .map { it.trim().trimEnd('/') }
val escapedApiBaseUrl = apiBaseUrl.map {
    it.replace("\\", "\\\\").replace("\"", "\\\"")
}
val sanctumClientOrigin = providers.gradleProperty("CAMPAIGN_FIELD_SANCTUM_CLIENT_ORIGIN")
    .orElse("https://example.invalid")
    .map { it.trim().trimEnd('/') }
val escapedSanctumClientOrigin = sanctumClientOrigin.map {
    it.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "de.oliveroehme.campaignfield"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.oliveroehme.campaignfield"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"${escapedApiBaseUrl.get()}\"")
        buildConfigField(
            "String",
            "SANCTUM_CLIENT_ORIGIN",
            "\"${escapedSanctumClientOrigin.get()}\"",
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
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
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.maplibre.android)

    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.jvm)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
