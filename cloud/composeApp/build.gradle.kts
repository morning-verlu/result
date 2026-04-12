import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqlDelight)
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.ktor") {
            useVersion(libs.versions.ktor.get())
            because("Avoid Android D8 issue from mixed ktor transitive versions")
        }
        if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-datetime")) {
            useVersion(libs.versions.kotlinx.datetime.get())
            because("Force kotlinx-datetime to a single version (Compose Desktop + supabase-kt expect kotlinx.datetime.Instant class to exist)")
        }
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.datetime)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.storage)
            implementation(libs.supabase.functions)
            implementation(libs.kotlinx.serialization.json)
            // api: 保证 JVM 桌面 run 与 supabase-auth 反序列化共用同一套 kotlinx-datetime（含 UserSession 默认参数里的 Instant）
            api(libs.kotlinx.datetime)
            implementation(libs.qrose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.datetime)
        }
    }
}

android {
    val keystoreProps = Properties()
    val keystoreFile = rootProject.file("keystore.properties")
    val hasReleaseKeystore = keystoreFile.exists()
    if (hasReleaseKeystore) {
        keystoreFile.inputStream().use { keystoreProps.load(it) }
    }

    namespace = "cn.verlu.cloud"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "cn.verlu.cloud"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 8
        versionName = "1.0.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 正式环境：使用 keystore.properties 中的 release 签名；
            // 本地测试：若未配置 keystore，则回退 debug 签名，便于直接安装验证。
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // org.gradle.java.home requires Windows path in root gradle.properties.
        // Disable this false-positive check for the project-level properties file.
        disable += "PropertyEscape"
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

sqldelight {
    databases {
        create("CloudDatabase") {
            packageName.set("cn.verlu.cloud.db")
        }
    }
}

compose.desktop {
    application {
        mainClass = "cn.verlu.cloud.MainKt"
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
        )
        buildTypes.release.proguard {
            // Disable desktop ProGuard for now.
            // It strips runtime service metadata used by Ktor engine discovery in release package.
            isEnabled.set(false)
            configurationFiles.from(project.file("proguard-desktop-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "cn.verlu.cloud"
            packageVersion = "1.0.8"
            modules(
                "java.sql",
                "java.naming",
                "java.management",
                "java.desktop",
                "jdk.unsupported",
            )
            windows {
                iconFile.set(project.file("icons/cloud.ico"))
                shortcut = true
                menu = true
                perUserInstall = false
            }
            linux {
                iconFile.set(project.file("icons/cloud.png"))
            }
        }
    }
}
