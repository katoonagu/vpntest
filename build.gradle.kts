import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

extensions.findByName("kotlin")?.let { ext ->
    if (ext is KotlinProjectExtension) {
        ext.jvmToolchain(17)
    }
}
