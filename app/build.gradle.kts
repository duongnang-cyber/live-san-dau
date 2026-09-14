import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.vangnang.youtubelive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vangnang.livecamera"
        minSdk = 23
        targetSdk = 35
        versionCode = 26
        versionName = "1.25.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.github.pedroSG94.RootEncoder:library:2.7.2")
}

// Ship the actual dependency inventory and embedded notices without stripping upstream assets.
val runtimeNoticesDir = layout.buildDirectory.dir("generated/runtimeNotices")
val collectRuntimeNotices by tasks.registering {
    outputs.dir(runtimeNoticesDir)
    // Resolve once per build; notices must follow the resolved version, not an old hand-written list.
    outputs.upToDateWhen { false }
    doLast {
        val artifacts = configurations.getByName("debugRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .sortedBy { it.moduleVersion.id.toString() }
        val text = StringBuilder("RESOLVED RUNTIME DEPENDENCIES AND EMBEDDED NOTICES\n\n")
        fun isNotice(name: String): Boolean = Regex("(?i)(license|notice|copying).*|.*[._-](license|notice).*|.*\\.kotlin_module_LICENSE")
            .matches(name.substringAfterLast('/'))
        for (artifact in artifacts) {
            text.append("\n=== ").append(artifact.moduleVersion.id).append(" ===\n")
            val file = artifact.file
            if (file.extension !in listOf("jar", "aar")) continue
            ZipFile(file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory && isNotice(entry.name)) {
                        text.append("\n--- ").append(entry.name).append(" ---\n")
                        text.append(zip.getInputStream(entry).bufferedReader().use { it.readText() }).append('\n')
                    }
                    if (!entry.isDirectory && entry.name.endsWith(".jar")) {
                        ZipInputStream(ByteArrayInputStream(zip.getInputStream(entry).use { it.readBytes() })).use { nested ->
                            var part = nested.nextEntry
                            while (part != null) {
                                if (!part.isDirectory && isNotice(part.name)) {
                                    text.append("\n--- ").append(entry.name).append('/').append(part.name).append(" ---\n")
                                    text.append(nested.readBytes().toString(Charsets.UTF_8)).append('\n')
                                }
                                part = nested.nextEntry
                            }
                        }
                    }
                }
            }
        }
        val output = runtimeNoticesDir.get().file("licenses/RUNTIME_NOTICES.txt").asFile
        output.parentFile.mkdirs(); output.writeText(text.toString())
    }
}
android.sourceSets.getByName("main").assets.srcDir(runtimeNoticesDir)
tasks.named("preBuild").configure { dependsOn(collectRuntimeNotices) }
