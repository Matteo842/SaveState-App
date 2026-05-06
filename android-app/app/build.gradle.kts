plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.savestate.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.savestate.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.9.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Document File (SAF)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Explicit Fragment dependency to satisfy the InvalidFragmentVersionForActivityResult
    // lint check. The app itself is Compose + ComponentActivity and never
    // uses Fragments directly, but lint requires >= 1.3.0 whenever
    // registerForActivityResult() is present.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    
    // Root access (libsu)
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Optional secure build script (gitignored). When present it pulls in the
// real anti-piracy implementation and its dependencies. Public clones simply
// skip this file and fall back to the no-op StubLicenseGuardProvider.
val secureBuild = file("build-secure.gradle.kts")
if (secureBuild.exists()) {
    apply(from = secureBuild)
}

