# CNIC Scanner Library

[![](https://jitpack.io/v/abdulshaffay-imis/CnicScanner.svg)](https://jitpack.io/#abdulshaffay-imis/CnicScanner)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

An Android library for scanning and parsing Pakistani CNIC (Computerized National Identity Card) using ML Kit.

## Features

✅ **Multiple Capture Methods**
- Camera capture with ML Kit Document Scanner
- Gallery image selection
- Enhanced document scanner mode

✅ **OCR Processing**
- Powered by Google ML Kit Text Recognition
- Automatic CNIC data extraction
- Support for both front and back scanning

✅ **Customizable Parsing**
- Implement your own `CnicOcrParser` for custom parsing logic
- Flexible data extraction patterns
- Mergeable results from front and back scans

✅ **Clean Architecture**
- Kotlin Coroutines support
- Lightweight and easy to integrate
- Minimal dependencies

## Requirements

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34
- **Compile SDK**: 35
- **Kotlin**: 1.9+

## Installation

### Step 1: Add JitPack repository

Add the JitPack repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add the dependency

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.abdulshaffay-imis:CnicScanner:1.1.1")
}
```

### Step 3: Add required permissions

Add these permissions to your app's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
    android:maxSdkVersion="32" />
```

## Usage

### 1. Implement CnicOcrParser

Create your own parser implementation to extract CNIC data from OCR text:

```kotlin
import com.sspa.cnicscanner.entities.CnicEntity
import com.sspa.cnicscanner.ocr.CnicOcrParser

class MyCnicOcrParser : CnicOcrParser {
    override fun parse(ocrText: String, existing: CnicEntity?, isBackScan: Boolean): CnicEntity {
        val entity = existing ?: CnicEntity()
        
        // Your custom parsing logic here
        // Extract CNIC number, name, dates, etc.
        val cnicPattern = Regex("""\d{5}-\d{7}-\d""")
        val cnicMatch = cnicPattern.find(ocrText)
        
        if (cnicMatch != null) {
            entity.cnic = cnicMatch.value
        }
        
        // Parse other fields...
        
        return entity
    }
}
```

### 2. Initialize CnicScanner

```kotlin
import com.sspa.cnicscanner.CnicScanner
import com.sspa.cnicscanner.core.ImageSource
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var cnicScanner: CnicScanner
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize scanner with your custom parser
        val parser = MyCnicOcrParser()
        cnicScanner = CnicScanner(
            context = this,
            activity = this,
            ocrParser = parser
        )
    }
}
```

### 3. Scan CNIC

#### Scan from Camera

```kotlin
lifecycleScope.launch {
    try {
        // Scan front of CNIC
        val frontResult = cnicScanner.scanImage(
            imageSource = ImageSource.CAMERA,
            isBackScan = false
        )
        
        // Scan back of CNIC (preserves front data)
        val completeResult = cnicScanner.scanImage(
            imageSource = ImageSource.CAMERA,
            isBackScan = true
        )
        
        // Use the data
        println("CNIC: ${completeResult.cnic}")
        println("Name: ${completeResult.name}")
        println("Is Complete: ${completeResult.isComplete()}")
        
    } catch (e: Exception) {
        // Handle error
        Log.e("CNIC", "Scan failed", e)
    }
}
```

#### Scan from Gallery

```kotlin
lifecycleScope.launch {
    val result = cnicScanner.scanImage(
        imageSource = ImageSource.GALLERY,
        isBackScan = false
    )
}
```

#### Scan with Document Scanner

```kotlin
lifecycleScope.launch {
    val result = cnicScanner.scanImage(
        imageSource = ImageSource.DOCUMENT_SCANNER,
        isBackScan = false
    )
}
```

### 4. Handle Permissions

Request camera and storage permissions at runtime:

```kotlin
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted, proceed with scanning
            scanCnic()
        } else {
            // Handle permission denial
        }
    }
    
    private fun checkAndRequestPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
        )
    }
}
```

## API Reference

### CnicScanner

Main class for CNIC scanning operations.

```kotlin
class CnicScanner(
    context: Context,
    activity: Activity,
    ocrParser: CnicOcrParser
)
```

**Methods:**

- `suspend fun scanImage(imageSource: ImageSource, isBackScan: Boolean = false): CnicEntity`
  - Scans CNIC from specified source
  - Returns parsed CNIC data

### ImageSource

Enum for image capture sources:

```kotlin
enum class ImageSource {
    CAMERA,           // Direct camera capture
    GALLERY,          // Gallery selection
    DOCUMENT_SCANNER  // ML Kit Document Scanner
}
```

### CnicEntity

Data class holding CNIC information:

```kotlin
data class CnicEntity(
    var cnic: String = "",
    var name: String = "",
    var father_name: String = "",
    var date_of_birth: String = "",
    var cnic_issue_date: String = "",
    var cnic_expiry: String = "",
    var gender: String = "",
    var cnicHolderCountry: String = "",
    var present_address: String = "",
    var permanent_address: String = "",
    var cnic_front: String? = null,
    var cnic_back: String? = null
)
```

**Methods:**

- `fun isComplete(): Boolean` - Check if all essential fields are populated

### CnicOcrParser

Interface for custom OCR parsing:

```kotlin
interface CnicOcrParser {
    fun parse(
        ocrText: String, 
        existing: CnicEntity? = null, 
        isBackScan: Boolean = false
    ): CnicEntity
}
```

## Complete Example

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.sspa.cnicscanner.CnicScanner
import com.sspa.cnicscanner.core.ImageSource
import com.sspa.cnicscanner.entities.CnicEntity
import com.sspa.cnicscanner.ocr.CnicOcrParser
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var cnicScanner: CnicScanner
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize scanner
        cnicScanner = CnicScanner(
            context = this,
            activity = this,
            ocrParser = MyCnicOcrParser()
        )
        
        setContent {
            CnicScannerScreen()
        }
    }
    
    @Composable
    fun CnicScannerScreen() {
        var cnicData by remember { mutableStateOf<CnicEntity?>(null) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                lifecycleScope.launch {
                    cnicData = cnicScanner.scanImage(ImageSource.CAMERA)
                }
            }) {
                Text("Scan CNIC Front")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            cnicData?.let { data ->
                Text("CNIC: ${data.cnic}")
                Text("Name: ${data.name}")
                Text("DOB: ${data.date_of_birth}")
            }
        }
    }
}

// Custom parser implementation
class MyCnicOcrParser : CnicOcrParser {
    override fun parse(ocrText: String, existing: CnicEntity?, isBackScan: Boolean): CnicEntity {
        val entity = existing ?: CnicEntity()
        
        // CNIC Number Pattern
        val cnicPattern = Regex("""\d{5}-\d{7}-\d""")
        cnicPattern.find(ocrText)?.let {
            entity.cnic = it.value
        }
        
        // Add more parsing logic based on your CNIC format
        
        return entity
    }
}
```

## ProGuard Rules

The library includes consumer ProGuard rules. If you need to add custom rules:

```proguard
# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep library public API
-keep public class com.sspa.cnicscanner.** { *; }
```

## Dependencies

This library uses:

- **AndroidX Core KTX** (1.15.0)
- **AndroidX Activity KTX** (1.9.3)
- **Kotlin Coroutines** (1.9.0)
- **ML Kit Text Recognition** (16.0.1)
- **ML Kit Document Scanner** (16.0.0-beta1)

## License

```
Copyright 2025 SSPA Development Team

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues, questions, or feature requests, please open an issue on GitHub.

## Changelog

### Version 1.0.0
- Initial release
- Camera, Gallery, and Document Scanner support
- ML Kit OCR integration
- Customizable parsing interface
- Front and back CNIC scanning
