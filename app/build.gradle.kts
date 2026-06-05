import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}

// 加载本地签名配置
fun loadSigningProps(): Properties? {
    val propsFile = rootProject.file("app/keystore.properties")
    if (!propsFile.exists()) return null
    val props = Properties()
    FileInputStream(propsFile).use { props.load(it) }
    return props
}

val localProps = loadSigningProps()

val keystoreFile = System.getenv("RELEASE_KEYSTORE_PATH")?.let { file(it) }
    ?: localProps?.let { file(it.getProperty("storeFile")) }

val keystorePassword = System.getenv("RELEASE_STORE_PASSWORD")
    ?: localProps?.getProperty("storePassword")

val keyAliasName = System.getenv("RELEASE_KEY_ALIAS")
    ?: localProps?.getProperty("keyAlias")

val keyPasswordStr = System.getenv("RELEASE_KEY_PASSWORD")
    ?: localProps?.getProperty("keyPassword")

android {
    namespace = "com.example.niunantime"

    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = keystorePassword
            keyAlias = keyAliasName
            keyPassword = keyPasswordStr
        }
    }
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.niunantime"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.room.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    annotationProcessor(libs.room.compiler)
    implementation(libs.ucrop)
}