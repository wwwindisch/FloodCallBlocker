plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.libphonenumber)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
