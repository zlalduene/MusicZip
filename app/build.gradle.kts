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
    }


    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.arthenica:ffmpeg-kit-full:5.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
}