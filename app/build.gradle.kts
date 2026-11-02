plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePath = System.getenv("KEYSTORE_FILE") ?: "keystore/mipay.jks"
val keystorePassword = System.getenv("KEYSTORE_PASS").orEmpty()
val keyAliasName = System.getenv("KEYSTORE_ALIAS") ?: "mipaygpay"
val signingKeyPassword = System.getenv("KEY_PASS").takeUnless { it.isNullOrBlank() } ?: keystorePassword
val hasSigningConfig = keystorePassword.isNotEmpty() && file(keystorePath).exists()

android {
    namespace = "com.mipay.gpay.lsp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mipay.gpay.lsp"
        minSdk = 30
        targetSdk = 34
        versionCode = 5
        versionName = "1.5"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("mipay") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigningConfig) signingConfig = signingConfigs.getByName("mipay")
        }
        release {
            if (hasSigningConfig) signingConfig = signingConfigs.getByName("mipay")
            isMinifyEnabled = false
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
