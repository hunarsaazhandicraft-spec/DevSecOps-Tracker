import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.devsecops.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.devsecops.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-mvp"

        // TODO fill these from README.md after first sync (do NOT commit real keys to a public repo)
        buildConfigField("String", "SUPABASE_URL", "\"https://fbcgdabbplexpynnzvdy.supabase.co\"")
        buildConfigField("String", "APP_EMAIL", "\"${localProps.getProperty("app.email", "")}\"")
        buildConfigField("String", "APP_PASSWORD", "\"${localProps.getProperty("app.password", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_1X0vrnoB0aFa1ZUfSXWJuw_tt_MXrLT\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal, never-published app: sign the release build with the
            // debug key so it installs directly. A non-debuggable build is
            // much faster than the debug build for Compose.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Supabase (Kotlin multiplatform client) + Ktor engine for Android
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))  // 3.8.0 needs Kotlin 2.4 + newer androidx/datetime; 3.0.3 = Kotlin 2.1 / Ktor 3.0.x
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
