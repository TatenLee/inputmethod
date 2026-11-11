plugins { id("com.android.application") }
android {
    namespace = "indi.taten.smscode"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "indi.taten.smscode"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["androidTest"].java.srcDir(rootProject.file("tool-tests/src/androidTest/java"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
androidComponents.onVariants { variant ->
    val suffix = variant.name.replaceFirstChar { it.uppercase() }
    val merged = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
    val verify = tasks.register("verify${suffix}OfflinePolicy") {
        inputs.file(merged)
        doLast {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val document = factory.newDocumentBuilder().parse(merged.get().asFile)
            val ns = "http://schemas.android.com/apk/res/android"
            val uses = document.getElementsByTagName("uses-permission")
            val permissions = (0 until uses.length).map { (uses.item(it) as org.w3c.dom.Element).getAttributeNS(ns,"name") }
            check(permissions == listOf("android.permission.RECEIVE_SMS")
                    && document.getElementsByTagName("uses-permission-sdk-23").length == 0) { "验证码工具只允许新短信接收权限，发现：$permissions" }
            check(!document.documentElement.hasAttributeNS(ns, "sharedUserId")) { "必须使用独立 UID" }
            val application = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
            check(application.getAttributeNS(ns, "usesCleartextTraffic") == "false") { "禁止明文网络流量" }
        }
    }
    tasks.matching { it.name == "package$suffix" || it.name == "assemble$suffix" }.configureEach { dependsOn(verify) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
