plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mipay.gpay.lsp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mipay.gpay.lsp"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("mipay") {
            storeFile = file("keystore/mipay.jks")
            storePassword = "mipay1234"
            keyAlias = "mipaygpay"
            keyPassword = "mipay1234"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("mipay")
        }
        release {
            signingConfig = signingConfigs.getByName("mipay")
            isMinifyEnabled = false
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
}

dependencies {
    implementation("com.caverock:androidsvg-aar:1.4")
    compileOnly(files("libs/xposed-api.jar"))
}
