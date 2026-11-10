plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testFixturesImplementation(libs.kotlin.test)
}
