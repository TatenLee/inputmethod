plugins { id("com.android.library") }
android {
    namespace = "indi.tatenlee.inputmethod.speech"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    compileOnly(files(rootProject.file(".downloads/libraries/sherpa-onnx-1.13.8.aar")))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
}
