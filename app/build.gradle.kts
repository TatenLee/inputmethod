plugins { id("com.android.application") }
android {
    namespace = "indi.tatenlee.inputmethod"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "indi.tatenlee.inputmethod"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    androidResources { noCompress += listOf("onnx", "bin") }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/assets"))
    packaging { jniLibs { useLegacyPackaging = false } }
    buildTypes {
        // 同时精简测试包和正式包，去掉标准库中未被使用的 URL 读取等代码。
        all {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            testProguardFile("proguard-test-rules.pro")
        }
    }
}

// 依赖合并后再检查：Android Studio 和命令行直接构建也必须满足离线、无桌面入口约定。
androidComponents.onVariants { variant ->
    val capitalized = variant.name.replaceFirstChar { it.uppercase() }
    val merged = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
    val verifyPolicy = tasks.register("verify${capitalized}OfflinePolicy") {
        inputs.file(merged)
        doLast {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val document = factory.newDocumentBuilder().parse(merged.get().asFile)
            val namespace = "http://schemas.android.com/apk/res/android"
            fun names(tag: String): List<String> {
                val nodes = document.getElementsByTagName(tag)
                return (0 until nodes.length).map {
                    (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(namespace, "name")
                }
            }
            val permissions = names("uses-permission") + names("uses-permission-sdk-23")
            check(permissions.toSet() == setOf("android.permission.RECORD_AUDIO")) {
                "简心只允许麦克风权限，发现其他权限：$permissions"
            }
            check(names("category").none { it.endsWith("LAUNCHER") }) {
                "简心不提供桌面启动入口，请移除 Launcher intent-filter。"
            }
            check(!document.documentElement.hasAttributeNS(namespace, "sharedUserId")) {
                "简心必须使用独立 UID，不能继承其他应用的联网权限。"
            }
        }
    }
    tasks.matching { it.name == "package$capitalized" || it.name == "assemble$capitalized" }
        .configureEach { dependsOn(verifyPolicy) }
}
dependencies {
    implementation(project(":keyboard"))
    implementation(project(":settings"))
    implementation(project(":engine-rime"))
    implementation(project(":speech"))
    implementation(files(rootProject.file(".downloads/libraries/sherpa-onnx-1.13.8.aar")))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
