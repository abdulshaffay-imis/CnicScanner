# Quick Start Guide - CNIC Scanner Library

This guide will help you integrate the CNIC Scanner library into your Android app in just a few minutes.

## 1. Installation (2 minutes)

### Add JitPack Repository

In your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Add Dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.IMIS-Project:cnicscanner:1.0.0")
}
```

### Add Permissions

In your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

## 2. Create Parser (5 minutes)

Create a file `CnicParser.kt`:

```kotlin
import com.sspa.cnicscanner.entities.CnicEntity
import com.sspa.cnicscanner.ocr.CnicOcrParser

class CnicParser : CnicOcrParser {
    override fun parse(ocrText: String, existing: CnicEntity?, isBackScan: Boolean): CnicEntity {
        val entity = existing ?: CnicEntity()
        
        // Simple CNIC number extraction
        val cnicPattern = Regex("""(\d{5}-\d{7}-\d)""")
        cnicPattern.find(ocrText)?.let {
            entity.cnic = it.value
        }
        
        // Extract name (line after "Name")
        ocrText.lines().forEachIndexed { index, line ->
            if (line.contains("Name", ignoreCase = true) && index + 1 < ocrText.lines().size) {
                entity.name = ocrText.lines()[index + 1].trim()
            }
        }
        
        // Add more parsing logic as needed...
        
        return entity
    }
}
```

## 3. Request Permissions (3 minutes)

In your Activity/Composable:

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun CnicScannerScreen() {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.CAMERA] == true) {
            // Proceed with scanning
        }
    }
    
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
        )
    }
}
```

## 4. Initialize Scanner (2 minutes)

```kotlin
import com.sspa.cnicscanner.CnicScanner
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    
    private lateinit var cnicScanner: CnicScanner
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        cnicScanner = CnicScanner(
            context = this,
            activity = this,
            ocrParser = CnicParser()
        )
    }
}
```

## 5. Scan CNIC (3 minutes)

```kotlin
import com.sspa.cnicscanner.core.ImageSource
import kotlinx.coroutines.launch

// In your composable or activity
Button(onClick = {
    lifecycleScope.launch {
        try {
            // Scan front
            val result = cnicScanner.scanImage(
                imageSource = ImageSource.CAMERA,
                isBackScan = false
            )
            
            // Show results
            println("CNIC: ${result.cnic}")
            println("Name: ${result.name}")
            
        } catch (e: Exception) {
            // Handle error
            e.printStackTrace()
        }
    }
}) {
    Text("Scan CNIC")
}
```

## Complete Example (All in One)

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        
        cnicScanner = CnicScanner(
            context = this,
            activity = this,
            ocrParser = SimpleCnicParser()
        )
        
        setContent {
            MaterialTheme {
                CnicScannerApp()
            }
        }
    }
    
    @Composable
    fun CnicScannerApp() {
        var cnicData by remember { mutableStateOf<CnicEntity?>(null) }
        var isScanning by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    lifecycleScope.launch {
                        isScanning = true
                        try {
                            cnicData = cnicScanner.scanImage(ImageSource.CAMERA)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isScanning = false
                        }
                    }
                },
                enabled = !isScanning
            ) {
                Text(if (isScanning) "Scanning..." else "Scan CNIC")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            cnicData?.let { data ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("CNIC: ${data.cnic}", style = MaterialTheme.typography.bodyLarge)
                        Text("Name: ${data.name}", style = MaterialTheme.typography.bodyMedium)
                        Text("DOB: ${data.date_of_birth}", style = MaterialTheme.typography.bodyMedium)
                        Text("Gender: ${data.gender}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    
    // Simple parser implementation
    class SimpleCnicParser : CnicOcrParser {
        override fun parse(ocrText: String, existing: CnicEntity?, isBackScan: Boolean): CnicEntity {
            val entity = existing ?: CnicEntity()
            
            // Extract CNIC number
            Regex("""(\d{5}-\d{7}-\d)""").find(ocrText)?.let {
                entity.cnic = it.value
            }
            
            // Extract name (basic example)
            ocrText.lines().forEachIndexed { index, line ->
                when {
                    line.contains("Name", ignoreCase = true) && index + 1 < ocrText.lines().size -> {
                        entity.name = ocrText.lines()[index + 1].trim()
                    }
                    line.contains("Father", ignoreCase = true) && index + 1 < ocrText.lines().size -> {
                        entity.father_name = ocrText.lines()[index + 1].trim()
                    }
                }
            }
            
            return entity
        }
    }
}
```

## Testing

Run your app and:

1. Grant camera permission when prompted
2. Click "Scan CNIC" button
3. Point camera at CNIC front
4. Capture the image
5. View extracted data

## Next Steps

- Improve parser logic for better accuracy
- Add validation for extracted data
- Implement back side scanning for addresses
- Handle errors gracefully
- Add loading states and UI feedback

## Common Issues

### Camera Permission Denied
Make sure you request permission at runtime and handle denial gracefully.

### OCR Returns Empty
- Ensure good lighting when scanning
- CNIC should be clearly visible
- Try the `DOCUMENT_SCANNER` source for better results

### Activity Not ComponentActivity
The scanner requires `ComponentActivity`. Change your activity to extend it:
```kotlin
class MainActivity : ComponentActivity() { ... }
```

## Tips for Better Accuracy

1. **Good Lighting**: Ensure CNIC is well-lit
2. **Stable Position**: Hold camera steady
3. **Clear Image**: Avoid blurry or tilted images
4. **Document Scanner**: Use `ImageSource.DOCUMENT_SCANNER` for best results
5. **Parser Refinement**: Continuously improve your parser based on real CNIC formats

## Support

For detailed documentation, see [README.md](README.md)

For issues, visit: https://github.com/IMIS-Project/cnicscanner/issues
