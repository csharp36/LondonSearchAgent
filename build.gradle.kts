plugins {
    java
}

allprojects {
    group = "com.londonsearch"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}
