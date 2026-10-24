plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "org.studyfuse"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.studyfuse"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
        ndk { abiFilters += (providers.gradleProperty("studyfuseAbi").orNull?.let { listOf(it) } ?: listOf("arm64-v8a","x86_64")) }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    // Production signing is opt-in and external: private keys/passwords never enter the repository.
    val signingStore=providers.environmentVariable("STUDYFUSE_SIGNING_STORE").orNull
    if(!signingStore.isNullOrBlank()) {
        val production=signingConfigs.create("studyfuseProduction") {
            storeFile=file(signingStore)
            storePassword=providers.environmentVariable("STUDYFUSE_STORE_PASSWORD").get()
            keyAlias=providers.environmentVariable("STUDYFUSE_KEY_ALIAS").get()
            keyPassword=providers.environmentVariable("STUDYFUSE_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig=production
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    listOf("user.home","https.proxyHost","https.proxyPort","http.proxyHost","http.proxyPort","javax.net.ssl.trustStore").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key,it) }
    }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
