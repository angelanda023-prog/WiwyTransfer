import com.google.protobuf.gradle.id

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.protobuf")
}

android {
    namespace = "com.wiwy.wiwytransfer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wiwy.wiwytransfer"
        minSdk = 26
        targetSdk = 35
        // versionCode SIEMPRE creciente (interno, no se muestra) para poder instalar
        // sobre versiones anteriores sin desinstalar. versionName es lo que ve el usuario.
        versionCode = 20404
        versionName = "1.0.2"
    }

    signingConfigs {
        create("wiwy") {
            storeFile = file("../wiwy.keystore")
            storePassword = "wiwytransfer"
            keyAlias = "wiwy"
            keyPassword = "wiwytransfer"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("wiwy")
        }
        release {
            signingConfig = signingConfigs.getByName("wiwy")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.google.protobuf:protobuf-javalite:3.25.5")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
