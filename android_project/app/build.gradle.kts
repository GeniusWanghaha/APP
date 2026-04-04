import org.gradle.api.GradleException
import java.util.Properties
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun String.toBuildConfigString(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

val defaultDoubaoApiKey = "dda3449b-6ac6-426b-991c-709fd1803808"
val defaultDoubaoEndpointId = "ep-20260330170020-lptb9"
val defaultDoubaoBaseUrl = "https://ark.cn-beijing.volces.com/api/v3"

val doubaoApiKey = (project.findProperty("DOUBAO_API_KEY") as String?)
    ?: System.getenv("DOUBAO_API_KEY")
    ?: defaultDoubaoApiKey

val doubaoEndpointId = (project.findProperty("DOUBAO_ENDPOINT_ID") as String?)
    ?: System.getenv("DOUBAO_ENDPOINT_ID")
    ?: defaultDoubaoEndpointId

val doubaoBaseUrl = (project.findProperty("DOUBAO_BASE_URL") as String?)
    ?: System.getenv("DOUBAO_BASE_URL")
    ?: defaultDoubaoBaseUrl

val releaseSigningFile = rootProject.file("keystore/release-signing.properties")
val releaseSigningProps = Properties().apply {
    if (releaseSigningFile.exists()) {
        releaseSigningFile.inputStream().use(::load)
    }
}

fun resolveReleaseSigningValue(
    gradlePropertyName: String,
    envName: String,
    fileKey: String
): String? {
    return (project.findProperty(gradlePropertyName) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: releaseSigningProps.getProperty(fileKey)?.takeIf { it.isNotBlank() }
}

val releaseStoreFilePath = resolveReleaseSigningValue(
    gradlePropertyName = "RELEASE_STORE_FILE",
    envName = "RELEASE_STORE_FILE",
    fileKey = "storeFile"
)
val releaseStorePassword = resolveReleaseSigningValue(
    gradlePropertyName = "RELEASE_STORE_PASSWORD",
    envName = "RELEASE_STORE_PASSWORD",
    fileKey = "storePassword"
)
val releaseKeyAlias = resolveReleaseSigningValue(
    gradlePropertyName = "RELEASE_KEY_ALIAS",
    envName = "RELEASE_KEY_ALIAS",
    fileKey = "keyAlias"
)
val releaseKeyPassword = resolveReleaseSigningValue(
    gradlePropertyName = "RELEASE_KEY_PASSWORD",
    envName = "RELEASE_KEY_PASSWORD",
    fileKey = "keyPassword"
)

val hasReleaseSigning =
    !releaseStoreFilePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

val isReleaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

if (isReleaseTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        """
        缺少 Release 签名配置。
        请在以下任一位置提供：
        1) 环境变量：RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
        2) keystore/release-signing.properties（本地文件，不入库）
        """.trimIndent()
    )
}

android {
    namespace = "com.photosentinel.health"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.photosentinel.health"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DOUBAO_API_KEY", doubaoApiKey.toBuildConfigString())
        buildConfigField("String", "DOUBAO_ENDPOINT_ID", doubaoEndpointId.toBuildConfigString())
        buildConfigField("String", "DOUBAO_BASE_URL", doubaoBaseUrl.toBuildConfigString())
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("demo") {
            dimension = "edition"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
        create("contest") {
            dimension = "edition"
            applicationIdSuffix = ".contest"
            versionNameSuffix = "-contest"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as BaseVariantOutputImpl
            val flavorTag = if (flavorName.isNullOrBlank()) "base" else flavorName
            output.outputFileName = "PhotoSentinel-${flavorTag}-${buildType.name}-v$versionName.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
