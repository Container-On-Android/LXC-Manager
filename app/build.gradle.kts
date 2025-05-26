plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("app/testkey.jks")
            storePassword = "Dreamconnected"
            keyPassword = "Dreamconnected"
            keyAlias = "testkey"
        }
    }
    namespace = "io.dreamconnected.coa.lxcmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.dreamconnected.coa.lxcmanager"
        minSdk = 28
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    configurations.all {
        exclude(group = "androidx.appcompat", module = "appcompat")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.preference.ktx)
    implementation(libs.appcompat)
    implementation(libs.mpandroidchart)
    implementation(libs.dev.material.preference)

    implementation(project(":ReTerminal:application"))
    implementation(project(":ReTerminal:components"))
    implementation(project(":ReTerminal:main"))
    implementation(project(":ReTerminal:resources"))
    implementation(project(":ReTerminal:rish"))
}