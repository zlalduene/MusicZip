plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.music.musiczip"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.music.musiczip"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters.add("arm64-v8a")//只考虑x64版本
        }
        resourceConfigurations += listOf("zh", "en")
    }
    buildTypes {
        release {
            isMinifyEnabled = true      // R8 压缩、删除无用代码
            isShrinkResources = true    // 删除无用资源
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }
    }

    buildFeatures {
        viewBinding = true
    }

}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    //implementation("com.arthenica:ffmpeg-kit-full:5.1")//全量包 打包出的apk有100MB
    implementation("com.arthenica:ffmpeg-kit-audio:5.1")//增量包
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
}