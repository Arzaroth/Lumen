import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    id("com.android.application")
    jacoco
}

// The release key: from the environment in CI (secrets), from ~/.config/lumen/keystore.properties
// on a workstation. Without either, release builds are signed with the debug key, unless
// LUMEN_REQUIRE_RELEASE_KEY is set.
val signing = Properties().apply {
    val local = File(System.getProperty("user.home"), ".config/lumen/keystore.properties")
    if (local.exists()) local.inputStream().use { load(it) }
    System.getenv("LUMEN_KEYSTORE")?.let { setProperty("storeFile", it) }
    System.getenv("LUMEN_KEYSTORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("LUMEN_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("LUMEN_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}
val hasReleaseKey = signing.getProperty("storeFile")?.let { File(it).exists() } == true
if (!hasReleaseKey && System.getenv("LUMEN_REQUIRE_RELEASE_KEY") != null) {
    throw GradleException("LUMEN_REQUIRE_RELEASE_KEY is set but no release keystore was found")
}
val version = "1.1.4"

android {
    namespace = "com.arzaroth.lumen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arzaroth.lumen"
        minSdk = 26
        targetSdk = 34
        versionCode = version.split(".").fold(0) { code, part -> code * 100 + part.toInt() }
        versionName = version
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src")
        res.srcDirs("res")
        assets.srcDirs("assets")
    }
    sourceSets["test"].java.srcDirs("test")

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
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.maxHeapSize = "2g"
            }
        }
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

// Robolectric loads the app's classes through its own class loader, which JaCoCo skips by default.
tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.mockito:mockito-core:5.14.2")
}
