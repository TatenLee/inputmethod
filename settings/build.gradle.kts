plugins { id("com.android.library") }
android {
    namespace = "indi.tatenlee.inputmethod.settings"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { api(project(":core")) }
