plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt.android)
}

// Firebase (Crashlytics, FCM) needs a real google-services.json per environment (see
// docs/TECH_STACK.md §Environments). Dev/prod Catalyst+Firebase projects aren't both created
// yet, so these plugins are applied only once the matching config file exists — that keeps
// `./gradlew build` working for anyone who hasn't set up Firebase locally yet.
val hasGoogleServicesConfig =
  file("google-services.json").exists() ||
    listOf("dev", "prod").any { flavor -> file("src/$flavor/google-services.json").exists() }

if (hasGoogleServicesConfig) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.codigitech.belay"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.codigitech.belay"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "env"
    productFlavors {
        // Both flavors share the applicationId: the dev Firebase Android app is registered
        // under the bare package name, and Google Services matches applicationId exactly —
        // an applicationIdSuffix here would break dev's Firebase/Crashlytics wiring.
        create("dev") {
            dimension = "env"
            versionNameSuffix = "-dev"
        }
        create("prod") {
            dimension = "env"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Dependency injection
  implementation(libs.hilt.android)
  ksp(libs.hilt.android.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  // Firebase: crash reporting, auth, backend (Data Store -> Firestore, Functions -> callable)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.functions)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.kotlinx.serialization.json)

  // Local persistence (offline cache + sync queue, see docs/TECH_STACK.md §5)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.androidx.room.testing)
}
