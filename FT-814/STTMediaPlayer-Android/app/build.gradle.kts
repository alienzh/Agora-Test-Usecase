import org.jetbrains.kotlin.konan.properties.Properties
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val properties = Properties()
val inputStream = project.rootProject.file("local.properties").inputStream()
properties.load(inputStream)

android {
    namespace = "io.agora.sttmediaplayer"
    compileSdk = 33

    defaultConfig {
        applicationId = "io.agora.sttmediaplayer"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "APP_ID", "\"${properties.getProperty("APP_ID", "")}\"")
        buildConfigField("String", "APP_CERTIFICATE", "\"${properties.getProperty("APP_CERTIFICATE", "")}\"")
    }

    signingConfigs {
        register("release") {
            keyAlias = "key0"
            keyPassword = "123456"
            storeFile = file("./keystore/testkey.jks")
            storePassword = "123456"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        viewBinding = true
    }
    android.applicationVariants.all {
        // 编译类型
        val buildType = this.buildType.name
        outputs.all {
            // 判断是否是输出 apk 类型
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                this.outputFileName =
                    "Agora_STTMediaPlayer_V${defaultConfig.versionName}_${buildType}_${releaseTime()}.apk"
            }
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("io.agora:authentication:1.6.1")
    implementation("commons-codec:commons-codec:1.15")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
}

fun releaseTime(): String {
    return SimpleDateFormat("MMdd_HHmm", Locale.getDefault()).format(Date())
}