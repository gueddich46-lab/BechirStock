plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.bechir.stock"
 compileSdk = 35
 defaultConfig { applicationId = "com.bechir.stock"; minSdk = 24; targetSdk = 28; versionCode = 2; versionName = "0.2.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("com.google.zxing:core:3.5.3"); implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1") }
