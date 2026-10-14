plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.nineteenhome.lofimusic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nineteenhome.lofimusic"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 强制关闭代码混淆和压缩，这是解决打包后闪退最直接的方法
            isMinifyEnabled = false
            isShrinkResources = false

            // 保留这行，防止报错
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
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    //noinspection GradleDependency
    implementation("androidx.core:core:1.19.1")
    //noinspection GradleDependency
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    // ExoPlayer 核心库
    //noinspection UseTomlInstead
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    // ⚠️ 核心修复：必须包含这一行，否则 MediaSession 相关类全部报错
    //noinspection GradleDependency
    implementation("androidx.media:media:1.8.0")
    // OkHttp 网络请求库
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}