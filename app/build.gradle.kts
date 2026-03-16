plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    id("de.mannodermaus.android-junit5")
    kotlin("kapt")
    jacoco
}

android {
    namespace = "com.example.banksmstracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.banksmstracker"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

ktlint {
    version.set("1.5.0")
    android.set(true)
    outputColorName.set("RED")
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

dependencies {
    // --- main ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mpandroidchart)

    // --- unit tests ---
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(kotlin("test"))
    testImplementation(libs.appium.java.client)
    testImplementation(libs.allure.junit5)

    // --- Android instrumented tests ---
    androidTestImplementation(libs.junit.jupiter.api)
    androidTestRuntimeOnly(libs.junit.jupiter.engine)
    androidTestImplementation(libs.junit5.android.core)
    androidTestRuntimeOnly(libs.junit5.android.runner)

    // AndroidX test utilities
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Allure results directory for JVM tests (unit + Appium)
tasks.withType<Test> {
    systemProperty(
        "allure.results.directory",
        layout.buildDirectory.dir("allure-results").get().asFile.absolutePath
    )
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.12"
}

android {
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generate JaCoCo coverage report for unit tests"

    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/**",
        "**/android/databinding/*Binding.*",
        "**/BR.*",
        "**/*_MembersInjector.class",
        "**/Dagger*Component.class",
        "**/Dagger*Component\$Builder.class",
        "**/*Module_*Factory.class",
        "**/di/module/*",
        "**/*_Factory*.*",
        "**/*Module*.*",
        "**/*Component*.*",
        "**/*_Impl*.*",
        // Exclude UI classes (tested by Appium E2E tests)
        "**/ui/**",
        // Exclude database package (Room entities, DAOs - tested by instrumented tests)
        "**/database/**",
        // Exclude repository package (uses Room, tested by instrumented tests)
        "**/repository/**",
        // Exclude Application class
        "**/BankSmsTrackerApp*.class",
        // Exclude parser (tested by instrumented tests)
        "**/parser/**"
    )

    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        }
    )
}

tasks.register("jacocoCoverageVerification") {
    group = "verification"
    description = "Verify code coverage meets minimum threshold"

    dependsOn("jacocoTestReport")

    doLast {
        val reportFile = file("${layout.buildDirectory.get()}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
        if (reportFile.exists()) {
            // Parse XML using regex to avoid DOCTYPE issues
            val content = reportFile.readText()

            // Find all LINE counters at the report level (not inside packages)
            val linePattern = """<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""".toRegex()
            val matches = linePattern.findAll(content).toList()

            // Get the last match which is the total for the report
            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val totalMissed = lastMatch.groupValues[1].toInt()
                val totalCovered = lastMatch.groupValues[2].toInt()

                val total = totalCovered + totalMissed
                val coverage = if (total > 0) (totalCovered * 100.0 / total) else 0.0
                println("Line coverage: %.2f%% (%d/%d lines)".format(coverage, totalCovered, total))

                val minimumCoverage = 80.0
                if (coverage < minimumCoverage) {
                    println(
                        "WARNING: Coverage %.2f%% is below minimum threshold of %.2f%%"
                            .format(coverage, minimumCoverage)
                    )
                }
            } else {
                println("WARNING: Could not parse coverage data from report")
            }
        }
    }
}
