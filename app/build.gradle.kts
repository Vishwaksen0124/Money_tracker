import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Pull Supabase URL + anon key out of the gitignored local.properties at
// configuration time. Never committed; injected into BuildConfig.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val supabaseUrl = localProps.getProperty("SUPABASE_URL").orEmpty()
val supabaseAnonKey = localProps.getProperty("SUPABASE_ANON_KEY").orEmpty()
require(supabaseUrl.isNotBlank())     { "Set SUPABASE_URL in local.properties" }
require(supabaseAnonKey.isNotBlank()) { "Set SUPABASE_ANON_KEY in local.properties" }

android {
    namespace = "app.moneytracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.moneytracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        buildConfigField("String", "SUPABASE_URL",      "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Hardening §20.9: signing config must come from outside the repo.
            // Configure in ~/.gradle/init.d/ or via CI secrets, never check it in.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets {
        named("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        named("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    // WorkManager dep deferred to v0.4 — it transitively contributes
    // ACCESS_NETWORK_STATE, which would need a tools:node="remove" suppression.

    // Supabase: client SDK + pure-Kotlin ktor engine (CIO) so we don't pull
    // in ktor-client-android, which contributes ACCESS_NETWORK_STATE.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// Hardening §21.1 — release-blocking build check (updated for cloud).
// INTERNET is now required (Supabase). Everything that smells like network
// state introspection or extra wireless capability stays banned — those are
// what analytics / ad / crash-reporter SDKs typically pull in.
// ---------------------------------------------------------------------------
abstract class VerifyNoNetworkPermissionTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val file = mergedManifest.get().asFile
        val banned = setOf(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
        )
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
        val ns = "http://schemas.android.com/apk/res/android"
        val nodes = doc.getElementsByTagName("uses-permission")
        for (i in 0 until nodes.length) {
            val name = (nodes.item(i) as Element).getAttributeNS(ns, "name")
            if (name in banned) {
                throw GradleException(
                    "Hardening §21.1 violated: '$name' present in merged manifest at ${file.path}. " +
                    "Only INTERNET is allowed (for Supabase). Network-introspection " +
                    "permissions are typically added by analytics / ad / crash-reporter " +
                    "libraries. Locate the offending dependency and remove it."
                )
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val capName = variant.name.replaceFirstChar { it.uppercase() }
        val verifyTask = tasks.register(
            "verifyNoNetworkPermission$capName",
            VerifyNoNetworkPermissionTask::class.java
        ) {
            mergedManifest.set(
                variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            )
        }
        tasks.matching { it.name == "assemble$capName" }.configureEach {
            dependsOn(verifyTask)
        }
    }
}
