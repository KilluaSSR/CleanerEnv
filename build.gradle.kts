plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register<Exec>("buildNative") {
    group = "build"
    description = "Builds native/ (cargo-ndk) into native/out."
    workingDir = file("native")
    commandLine("./build.sh")
    inputs.files(fileTree("native") { include("**/*.rs", "**/*.java", "**/Cargo.toml", "Cargo.lock", "build.sh") })
    inputs.dir("module")
    outputs.dir("native/out")
}
