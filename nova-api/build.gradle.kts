import org.jetbrains.dokka.gradle.AbstractDokkaLeafTask

plugins {
    java
    `maven-publish`
    alias(libs.plugins.dokka)
}

dependencies {
    implementation("org.jetbrains:annotations:25.0.0")
    compileOnly(libs.paper.api)
}

tasks.withType<AbstractDokkaLeafTask> {
    dokkaSourceSets {
        register("main") {
            sourceRoots.from("src/main/java")
        }
    }
}

publishing {
    repositories {
        maven {
            credentials {
                name = "xenondevs"
                url = uri { "https://repo.xenondevs.xyz/releases/" }
                credentials(PasswordCredentials::class)
            }
        }
    }
    
    publications {
        create<MavenPublication>("novaAPI") {
            from(components.getByName("java"))
            artifact(tasks.getByName("sources"))
        }
    }
}