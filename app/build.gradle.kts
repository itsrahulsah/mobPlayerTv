plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android") // We will apply this later when configuring dagger
}

kotlin {
    jvmToolchain(17)
}

// Keep the root test client as the single source, packaging only that file.
val testClientAssets = layout.buildDirectory.dir("generated/testClientAssets")
val syncTestClientAssets by tasks.registering(Sync::class) {
    from(rootProject.file("test_client.html"))
    into(testClientAssets)
}

android {
    namespace = "com.mobplayer.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mobplayer.tv"
        minSdk = 26
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    sourceSets.getByName("main").assets.srcDir(testClientAssets)
}

tasks.named("preBuild") {
    dependsOn(syncTestClientAssets)
}

dependencies {
    // YouTube crawler (feeds + stream extraction)
    implementation(project(":youtubecrawler"))

    // SmartTube MediaServiceCore AARs (YouTube stream deciphering + PO tokens) & their dependencies
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("com.github.florianingerl.util:regex:1.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("com.grack:nanojson:1.7")
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("dnsjava:dnsjava:2.1.9")
    implementation("com.jakewharton:disklrucache:2.0.2")
    implementation("info.guardianproject.netcipher:netcipher:2.1.0")

    // Core & Architecture
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Compose for TV & UI
    implementation("androidx.compose.ui:ui:1.6.2")
    implementation("androidx.compose.ui:ui-graphics:1.6.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.2")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-core:1.6.2")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Ktor Server (WebSockets & Networking)
    val ktorVersion = "2.3.9"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    
    // Logging implementation for Ktor (SLF4J to Android Logcat)
    implementation("org.slf4j:slf4j-android:1.7.36")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Media3 (ExoPlayer & MediaSession, HLS, DASH, RTSP)
    val media3Version = "1.3.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Security
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-android-compiler:2.55")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.2")
}

dependencies {
}
