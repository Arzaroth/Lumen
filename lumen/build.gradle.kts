import java.util.Properties

plugins {
    id("com.android.application")
}

// The release key: from the environment in CI (secrets), from ~/.config/lumen/signing.properties
// on a workstation. Without either, release builds are signed with the debug key.
val signing = Properties().apply {
    val local = File(System.getProperty("user.home"), ".config/lumen/signing.properties")
    if (local.exists()) local.inputStream().use { load(it) }
    System.getenv("LUMEN_KEYSTORE")?.let { setProperty("storeFile", it) }
    System.getenv("LUMEN_KEYSTORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("LUMEN_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("LUMEN_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}
val hasReleaseKey = signing.getProperty("storeFile")?.let { File(it).exists() } == true

android {
    namespace = "dev.arzaroth.lumen"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.arzaroth.lumen"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src")
        res.srcDirs("res")
        assets.srcDirs("assets")
    }

    androidResources {
        noCompress += "ttf"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        lintConfig = file("lint.xml")
    }
}

base {
    archivesName.set("lumen")
}
