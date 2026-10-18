import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    kotlin("jvm") version "2.3.0-RC"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "9.4.3"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "fr.redsavant"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://jitpack.io")
    maven("https://raw.githubusercontent.com/dominicfeliton/SmartInvs/bukkit-maven-repo")
}

dependencies {

    compileOnly("org.projectlombok:lombok:1.18.42")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    implementation("com.github.RedSavant:RSUtils:v1.1.1")
    implementation("fr.minuskube.inv:bukkit-smart-invs:1.5.0")

    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
    }
}

var targetJavaVersion = 21

kotlin {
    jvmToolchain(targetJavaVersion)
}


tasks.withType<io.papermc.paperweight.tasks.JavaLauncherTask>().configureEach {
    launcher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        }
    )
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    relocate("fr.minuskube.inv", "fr.redsavant.discordlink.libs.smartinvs")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

val botDir = file("bot")

val installBotDeps by tasks.registering(Exec::class) {
    workingDir(botDir)
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("cmd", "/c", "pnpm install")
    } else {
        commandLine("sh", "-c", "pnpm install")
    }
}

val buildBot by tasks.registering(Exec::class) {
    dependsOn(installBotDeps)
    workingDir(botDir)
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        commandLine("cmd", "/c", "pnpm build")
    } else {
        commandLine("sh", "-c", "pnpm build")
    }
}

tasks.processResources {
    dependsOn(buildBot)
}

sourceSets {
    main {
        resources {
            srcDir("bot/dist")
            include("index.js", "plugin.yml", "config.yml", "languages/**", "legacy/**", "emojis.yml")
        }
    }
}
