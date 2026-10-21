plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.kglabs28.yapnplay"
version = "0.1.0"

application {
    mainClass.set("com.kglabs28.yapnplay.backend.ApplicationKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.koin.core)
    implementation(libs.koin.logger.slf4j)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    // Local development only: a real PostgreSQL started by the server
    // itself when DB_URL is not set, so no Docker is needed on the laptop.
    implementation(libs.embedded.postgres)
    implementation(libs.logback.classic)
    implementation(libs.sentry)
    implementation(libs.sentry.logback)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
}

tasks.test {
    useJUnitPlatform()
    // `gradlew test -Dgolden.update=true` rewrites the golden JSON files (see GoldenContractTest).
    systemProperty("golden.update", providers.systemProperty("golden.update").getOrElse("false"))
}


// The deployed fat JAR: libraries register plugins in META-INF/services files
// (Flyway core + its PostgreSQL module both do). Merge them instead of letting
// one overwrite the other, or Flyway finds no SQL migrations in production.
tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    mergeServiceFiles()
}

// Offline bot arena (src/test/.../arena/BotArena.kt): seeded bot-vs-bot games to compare bot settings.
// ./gradlew arena -Parena="game=citybazaar games=200 B.ruinWeightPct=150"
tasks.register<JavaExec>("arena") {
    group = "verification"
    description = "Runs seeded bot-vs-bot games and compares two bot setting sets"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.kglabs28.yapnplay.backend.arena.BotArenaKt")
    args = (project.findProperty("arena") as String?)?.split(' ') ?: emptyList()
}
