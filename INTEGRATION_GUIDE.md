# CNIC Scanner Library - Integration Guide

✅ **Successfully published to GitHub!**

Repository: https://github.com/abdulshaffay-imis/CnicScanner  
Version: v1.0.0

## 🎉 JitPack Setup

### Step 1: Build on JitPack

1. Visit: **https://jitpack.io**
2. Enter: `abdulshaffay-imis/CnicScanner`
3. Click **"Look up"**
4. Select version **`v1.0.0`**
5. Click **"Get it"**
6. Wait for build to complete (green checkmark ✓)

### Step 2: Add to Your IMIS Mobile App

In your **main app** (`D:\Kotlin\imis-mobile`):

#### 1. Update `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }  // Add this line
    }
}
```

#### 2. Update `app/build.gradle.kts`

```kotlin
dependencies {
    // Replace local module dependency with remote package
    implementation("com.github.abdulshaffay-imis:CnicScanner:1.0.0")
    
    // Remove this if you had it:
    // implementation(project(":cnicscanner"))
}
```

#### 3. Remove Local Module

From `settings.gradle.kts`, remove:
```kotlin
include(":cnicscanner")  // Remove this line
```

#### 4. Update Imports in Your Code

No changes needed! The package name remains the same:
```kotlin
import com.sspa.cnicscanner.CnicScanner
import com.sspa.cnicscanner.core.ImageSource
import com.sspa.cnicscanner.entities.CnicEntity
import com.sspa.cnicscanner.ocr.CnicOcrParser
```

#### 5. Sync and Build

```powershell
cd D:\Kotlin\imis-mobile
.\gradlew clean
.\gradlew build
```

## 📦 Usage (Same as Before)

```kotlin
// Initialize scanner
val scanner = CnicScanner(
    context = this,
    activity = this,
    ocrParser = YourCnicOcrParser()
)

// Scan CNIC
lifecycleScope.launch {
    val result = scanner.scanImage(ImageSource.CAMERA, isBackScan = false)
    println("CNIC: ${result.cnic}")
}
```

## 🔄 Updating the Library

When you make changes to the library:

1. **Make changes** in `D:\Kotlin\CnicScanner`
2. **Commit** changes
3. **Update version** in `gradle.properties`:
   ```properties
   VERSION_NAME=1.0.1
   ```
4. **Tag and push**:
   ```powershell
   git add .
   git commit -m "Update: description of changes"
   git push
   git tag -a v1.0.1 -m "Version 1.0.1 - Bug fixes"
   git push origin v1.0.1
   ```
5. **Build on JitPack** (visit jitpack.io)
6. **Update dependency** in your app:
   ```kotlin
   implementation("com.github.abdulshaffay-imis:CnicScanner:1.0.1")
   ```

## ✅ Benefits of This Approach

1. **Clean separation**: Library code separate from app code
2. **Reusable**: Can use in multiple projects
3. **Version control**: Proper versioning and release management
4. **No local dependency**: App doesn't need library source code
5. **Easy updates**: Just change version number in app

## 📝 Repository Structure

Your library repository now has:
- ✅ Complete source code
- ✅ Build configuration for JitPack
- ✅ Comprehensive documentation (README, QUICKSTART)
- ✅ Publishing instructions
- ✅ Apache 2.0 License
- ✅ GitHub Actions CI/CD (optional)
- ✅ Sample parser implementation

## 🔗 Links

- **GitHub**: https://github.com/abdulshaffay-imis/CnicScanner
- **JitPack**: https://jitpack.io/#abdulshaffay-imis/CnicScanner
- **Documentation**: See README.md in repository

## 🆘 Troubleshooting

### JitPack Build Failed
- Check build log on JitPack
- Verify tag was pushed: `git push origin v1.0.0`
- Ensure build.gradle.kts has maven-publish plugin

### Cannot Resolve Dependency
- Verify JitPack repository is added
- Check version number matches tag
- Wait for JitPack build to complete (green checkmark)
- Try clearing Gradle cache: `.\gradlew clean --refresh-dependencies`

### Build Fails in Main App
- Remove local `:cnicscanner` module from settings.gradle.kts
- Sync Gradle files
- Clean and rebuild

## 📞 Support

For issues with the library:
- GitHub Issues: https://github.com/abdulshaffay-imis/CnicScanner/issues

---

**Status**: ✅ Published and ready to use!  
**Next Step**: Add to JitPack and integrate into your main app
