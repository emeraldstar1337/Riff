plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "dev.lad"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.lad.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.9.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val signingStore =
        providers
            .gradleProperty("ladSigningStore")
            .orElse(providers.environmentVariable("LAD_SIGNING_STORE"))
    val signedBuild = signingStore.isPresent
    signingConfigs {
        if (signedBuild)
            create("personal") {
                storeFile = rootProject.file(signingStore.get())
                storePassword =
                    providers
                        .gradleProperty("ladStorePassword")
                        .orElse(providers.environmentVariable("LAD_STORE_PASSWORD"))
                        .get()
                keyAlias =
                    providers
                        .gradleProperty("ladKeyAlias")
                        .orElse(providers.environmentVariable("LAD_KEY_ALIAS"))
                        .get()
                keyPassword =
                    providers
                        .gradleProperty("ladKeyPassword")
                        .orElse(providers.environmentVariable("LAD_KEY_PASSWORD"))
                        .get()
            }
    }
    buildTypes {
        getByName("debug") { if (signedBuild) signingConfig = signingConfigs.getByName("personal") }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signedBuild) signingConfig = signingConfigs.getByName("personal")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    bundle { language { enableSplit = false } }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.4.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
