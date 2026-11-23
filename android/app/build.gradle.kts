import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// 릴리즈 서버 주소 (운영 도메인을 코드에 넣지 않는다). 우선순위: -PreleaseServerUrl=... > android/local.properties의
// release.server.url > placeholder. 예: release.server.url=https://api.your-domain.com (끝에 / 없이, HTTPS만 동작)
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseServerUrl: String = (
    providers.gradleProperty("releaseServerUrl").orNull
        ?: localProperties.getProperty("release.server.url")
        ?: "https://api.example.invalid"
    ).trimEnd('/')
if (releaseServerUrl.endsWith("example.invalid")) {
    logger.warn("WARNING: release.server.url 이 설정되지 않아 릴리즈 빌드가 placeholder 주소($releaseServerUrl)를 가리킵니다. docs/DEPLOY_TODO.md 참고.")
}

val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.fashionapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fashionapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // API·OAuth2 모두 localhost:8080 → 에뮬레이터/실기기에서 `adb reverse tcp:8080 tcp:8080` 필요 (docs/LOCAL_DEV.md).
            // OAuth2 redirect_uri는 Google/Kakao 콘솔에 IP를 등록할 수 없어 localhost여야 하고, API도 같은 주소로 맞춰
            // 에뮬레이터 전용 10.0.2.2 없이 USB로 연결한 실기기에서도 같은 빌드로 쓸 수 있게 함
            buildConfigField("String", "BASE_URL", "\"http://localhost:8080/api/\"")
            buildConfigField("String", "OAUTH2_BASE_URL", "\"http://localhost:8080\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 운영 서버 주소는 설정으로 주입 (위 releaseServerUrl). 릴리즈는 cleartext가 막혀 있어 https여야 한다
            buildConfigField("String", "BASE_URL", "\"$releaseServerUrl/api/\"")
            buildConfigField("String", "OAUTH2_BASE_URL", "\"$releaseServerUrl\"")
            signingConfig = signingConfigs.getByName("release")
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

    // ViewModel unit tests run on the JVM; android.jar stubs return defaults instead of throwing
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.browser)
    implementation(libs.coroutines.android)
    // FR-21: on-device background removal (ADR 0020)
    implementation(libs.mlkit.subject.segmentation)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
