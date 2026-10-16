import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Robolectric 的 android-all jar 缓存目录。
 *
 * 工具链装在 C 盘（含 JDK / SDK / Gradle），项目本身可能被移到别的盘，所以这里
 * 不能只写「项目上一级」这种相对路径 —— 项目搬到 D 盘后它会解析成
 * D:\.android-toolchain\robolectric-deps，那个目录并不存在，Robolectric 就会去
 * Maven Central 下载 android-all（本机不可达），测试全挂。
 *
 * 查找顺序：-Probolectric.deps=... 显式指定 → 项目上一级 → 工具链绝对路径。
 */
val robolectricDeps: String = run {
    val explicit = providers.gradleProperty("robolectric.deps").orNull
    if (explicit != null) return@run explicit
    val candidates = listOf(
        File(rootDir.parentFile, ".android-toolchain/robolectric-deps"),
        File("C:/Users/H/Downloads/.android-toolchain/robolectric-deps"),
    )
    candidates.firstOrNull { it.isDirectory }?.absolutePath
        ?: candidates.first().absolutePath
}

/**
 * 签名配置。
 *
 * keystore 和口令**不入库**：口令一旦公开，任何人都能用同一把密钥签一个「更新包」
 * 覆盖用户手机上装的正版 App。所以这里改成「有就用，没有就跳过」：
 *
 * - `app/tsubasa.keystore` 存在，且 `keystore.properties`（同样不入库）里有口令 → 正常签名；
 * - 两者缺一 → release 构建产出**未签名** APK（`app-release-unsigned.apk`），
 *   而不是让构建直接失败。clone 下来的人没有密钥也能照常编译、跑测试。
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseKeystore = file("tsubasa.keystore")
val canSign = releaseKeystore.exists() && keystoreProps.getProperty("storePassword") != null

android {
    namespace = "com.tsubasa.notes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tsubasa.notes"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "2.3"
        resourceConfigurations += listOf("zh", "en", "ja")
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // 只签 v2：minSdk 是 26（Android 8.0），而 v2 从 Android 7.0 就支持了，
                // 所以 v1（JAR 签名）在这里没有意义 —— 只有需要兼容 API 24 以下才要开。
                enableV1Signing = false
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            // Material3 / Foundation 里不少 API 仍标着 Experimental，
            // 这里统一放开，免得每个用到的地方都写一遍 @OptIn。
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            // FlowRow 在 1.6.x 还标着 ExperimentalLayoutApi（色板要换行，
            // 单行 8 个色块在窄屏上会被挤出去）。
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // Robolectric 运行时要去 Maven 下载 android-all jar，默认走 Maven Central（本机不可达），
            // 这里指到腾讯镜像；同时把下载/锁文件目录指到可写位置
            // （默认会往用户主目录写锁文件，本机被拒）。
            it.systemProperty(
                "robolectric.dependency.repo.url",
                "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"
            )
            it.systemProperty("robolectric.dependency.dir", robolectricDeps)
            it.testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = true
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
}
