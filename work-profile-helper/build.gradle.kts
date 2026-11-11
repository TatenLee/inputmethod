plugins { id("com.android.application") }
android {
    namespace = "indi.tatenlee.workprofile"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "indi.tatenlee.workprofile"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "indi.tatenlee.workprofile.control.WorkProfileControl"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
