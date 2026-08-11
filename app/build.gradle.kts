plugins {
    alias(libs.plugins.androidApplication)
}

android {
    signingConfigs {
        create("release") {
            storeFile =
                file("C:\\Users\\kano\\AndroidStudioProjects\\CFS_HRV\\CFS_HRV_keystore.jks")
            keyAlias = "key0"
            storePassword = "Stanaway12"
            keyPassword = "Stanaway12"
        }
    }
    namespace = "com.vitahot.ms_battery_nz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vitahot.ms_battery_nz"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.camera.core)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.video)
    implementation(libs.camera.camera2)
    implementation(libs.camera.extensions)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.google.code.gson:gson:2.14.0")
}