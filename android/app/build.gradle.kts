import java.util.Properties

plugins {
    id("com.android.application")
}

// 从 keystore.properties 读取签名信息（该文件已在 .gitignore 中，不进仓库）
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.xiaoman.ledger"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xiaoman.ledger"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false        // 单 Activity，混淆收益不大，且便于用户审计
            isShrinkResources = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // assets/www 里不放编译产物，但打包时必须是完整目录
    androidResources {
        noCompress += "html"
    }
}

/**
 * 把仓库根目录的 index.html 同步进 assets。
 * 这样「网页是唯一真源」，不再维护两份 HTML。
 */
val syncWebAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../index.html"))
    into(layout.projectDirectory.dir("src/main/assets/www"))
}

tasks.named("preBuild") {
    dependsOn(syncWebAssets)
}
