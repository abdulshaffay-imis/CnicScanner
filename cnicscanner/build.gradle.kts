plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("maven-publish")
}

android {
    namespace = "com.sspa.cnicscanner"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.10.1")
    
    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // ML Kit for Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")
    
    // ML Kit Document Scanner
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                
                groupId = "com.sspa"
                artifactId = "cnicscanner"
                version = "1.0.0"
                
                pom {
                    name.set("CNIC Scanner")
                    description.set("Android library for scanning Pakistani CNIC cards using ML Kit")
                    url.set("https://github.com/IMIS-Project/cnicscanner")
                    
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("sspa")
                            name.set("SSPA Development Team")
                        }
                    }
                }
            }
        }
    }
}
