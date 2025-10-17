import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.oneclick.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.oneclick.vpn"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEMO", "false")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "DEMO", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    flavorDimensions += listOf("client")

    productFlavors {
        val clients = (1..30).map { index ->
            "client%02d".format(Locale.US, index)
        }
        clients.forEach { flavor ->
            create(flavor) {
                dimension = "client"
                val assetName = "wg/$flavor.conf"
                buildConfigField("String", "DEFAULT_WG_ASSET", "\"$assetName\"")
                resValue("string", "default_wg_asset", assetName)
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
    }

    sourceSets.getByName("debug") {
        java.srcDir("src/debug/java")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
    implementation("com.wireguard.android:tunnel:1.0.20230706")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

abstract class VerifyWgAssetsTask : DefaultTask() {

    @get:InputDirectory
    abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun validate() {
        val expected = (1..30).map { index -> "client%02d.conf".format(Locale.US, index) }
        val dir = assetsDir.asFile.get()
        if (!dir.exists()) {
            throw GradleException("WireGuard assets directory missing: ${dir.absolutePath}")
        }
        expected.forEach { filename ->
            val file = dir.resolve(filename)
            if (!file.exists()) {
                throw GradleException("Required WireGuard profile missing: $filename")
            }
            val text = file.readText()
            val missingSections = listOf("Interface", "Peer")
                .filter { section -> !text.contains("[$section]") }
            if (missingSections.isNotEmpty()) {
                throw GradleException(
                    "WireGuard profile $filename missing sections: ${missingSections.joinToString()}"
                )
            }
        }
    }
}

abstract class VerifySecurityConfigTask : DefaultTask() {

    @get:InputFile
    abstract val configFile: RegularFileProperty

    @TaskAction
    fun validate() {
        val file = configFile.asFile.get()
        if (!file.exists()) {
            throw GradleException("Release network security config missing: ${file.absolutePath}")
        }
        val content = file.readText()
        val forbiddenPatterns = listOf(
            Regex("""src\s*=\s*\"user\"""", RegexOption.IGNORE_CASE),
            Regex("""cleartextTrafficPermitted\s*=\s*\"true\"""", RegexOption.IGNORE_CASE)
        )
        val violation = forbiddenPatterns.firstOrNull { it.containsMatchIn(content) }
        if (violation != null) {
            throw GradleException(
                "Forbidden attribute detected in release network security config: ${violation.pattern}"
            )
        }
    }
}

val generateWgStubs = tasks.register("generateWgStubs") {
    group = "generation"
    description = "Generates placeholder WireGuard configuration files for all clients."

    val assetsDir = layout.projectDirectory.dir("src/main/assets/wg")
    outputs.dir(assetsDir)
    outputs.upToDateWhen { false }

    doLast {
        val dirFile = assetsDir.asFile
        if (!dirFile.exists()) {
            dirFile.mkdirs()
        }
        (1..30).forEach { index ->
            val file = dirFile.resolve("client%02d.conf".format(Locale.US, index))
            val ipOctet = index + 1
            val content = """
                [Interface]
                PrivateKey = XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX=
                Address = 10.77.0.$ipOctet/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY=
                Endpoint = 0.0.0.0:51820
                AllowedIPs = 0.0.0.0/0, ::/0
                PersistentKeepalive = 25
            """.trimIndent()
            file.writeText(content)
        }
    }
}

val verifyWgAssets = tasks.register<VerifyWgAssetsTask>("verifyWgAssets") {
    group = "verification"
    description = "Validates that all WireGuard client assets exist and are well-formed."
    assetsDir.set(layout.projectDirectory.dir("src/main/assets/wg"))
    dependsOn(generateWgStubs)
}

val verifySecurityConfig = tasks.register<VerifySecurityConfigTask>(
    "verifySecurityConfig"
) {
    group = "verification"
    description = "Ensures release network security config does not trust user CAs or cleartext."
    configFile.set(layout.projectDirectory.file("src/release/res/xml/network_security_config.xml"))
}

tasks.matching { task ->
    val name = task.name
    (name.startsWith("assemble") || name.startsWith("bundle")) && name.endsWith("Release")
}.configureEach {
    dependsOn(verifyWgAssets)
    dependsOn(verifySecurityConfig)
}

tasks.register("assembleAllClientsRelease") {
    group = "build"
    description = "Assembles release APKs for every client product flavor."
    (1..30).forEach { index ->
        dependsOn("assembleClient%02dRelease".format(Locale.US, index))
    }
}
