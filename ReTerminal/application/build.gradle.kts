@file:Suppress("UnstableApiUsage", "UnstableApiUsage", "UnstableApiUsage", "UnstableApiUsage",
    "UnstableApiUsage", "UnstableApiUsage", "UnstableApiUsage", "UnstableApiUsage"
)

plugins {
    id("com.android.library")
    id("kotlin-android")
    alias(libs.plugins.compose.compiler)
}


android {
    namespace = "com.rk.application"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
       // isCoreLibraryDesugaringEnabled = true
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    testOptions {
        targetSdk = 35
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":ReTerminal:main"))

}
