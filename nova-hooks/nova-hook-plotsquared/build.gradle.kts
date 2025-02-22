plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.paperweight)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
    implementation(project(":nova"))
    implementation(project(":nova-api"))
    compileOnly("com.intellectualsites.plotsquared:plotsquared-core:7.4.2")
    compileOnly("com.intellectualsites.plotsquared:plotsquared-bukkit:7.4.2") { isTransitive = false }
}