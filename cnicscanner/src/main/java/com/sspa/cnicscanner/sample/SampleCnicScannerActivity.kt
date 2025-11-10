package com.sspa.cnicscanner.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

/**
 * Sample Activity demonstrating CNIC Scanner usage.
 * 
 * To use this in your app:
 * 
 * 1. Add this activity to your AndroidManifest.xml:
 * ```xml
 * <activity
 *     android:name="com.sspa.cnicscanner.sample.SampleCnicScannerActivity"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.MAIN" />
 *         <category android:name="android.intent.category.LAUNCHER" />
 *     </intent-filter>
 * </activity>
 * ```
 * 
 * 2. Make sure to add required permissions in your AndroidManifest.xml:
 * ```xml
 * <uses-permission android:name="android.permission.CAMERA" />
 * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
 * <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
 * ```
 * 
 * 3. Launch the activity:
 * ```kotlin
 * val intent = Intent(this, SampleCnicScannerActivity::class.java)
 * startActivity(intent)
 * ```
 */
class SampleCnicScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface {
                    SampleCnicScannerScreen()
                }
            }
        }
    }
}
