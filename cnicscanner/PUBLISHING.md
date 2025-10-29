# Publishing CNIC Scanner Library to GitHub

This guide explains how to publish the CNIC Scanner library as a standalone package on GitHub for use with JitPack.

## Prerequisites

1. Git installed on your system
2. GitHub account
3. Android Studio or Gradle command line tools

## Step 1: Create a New GitHub Repository

1. Go to [GitHub](https://github.com) and create a new repository
   - Name: `cnicscanner` (or your preferred name)
   - Description: "Android library for scanning Pakistani CNIC cards using ML Kit"
   - Visibility: Public (required for JitPack free tier)
   - Initialize with README: No (we'll push our own)

2. Copy the repository URL

## Step 2: Prepare the Library Code

The library is already structured as a module in your workspace at `cnicscanner/`.

### Option A: Extract as Standalone Repository (Recommended)

Create a new repository with only the library:

```powershell
# Navigate to your workspace
cd D:\Kotlin\imis-mobile

# Create a new directory for the standalone library
mkdir ..\cnicscanner-standalone
cd ..\cnicscanner-standalone

# Initialize git
git init

# Copy library module
Copy-Item -Path ..\imis-mobile\cnicscanner\* -Destination . -Recurse

# Create root build.gradle.kts
```

Create `build.gradle.kts` in the root:

```kotlin
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

Create `settings.gradle.kts` in the root:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cnicscanner"
include(":cnicscanner")
```

Update the library's `build.gradle.kts` module path references.

### Option B: Use as Submodule in Existing Repo

Keep it as a module in your current repository and publish from there.

## Step 3: Version and Tag

```powershell
# Add all files
git add .

# Commit
git commit -m "Initial release v1.0.0"

# Add remote (replace with your repo URL)
git remote add origin https://github.com/IMIS-Project/cnicscanner.git

# Push to main branch
git push -u origin main

# Create and push version tag (required for JitPack)
git tag -a v1.0.0 -m "Version 1.0.0 - Initial release"
git push origin v1.0.0
```

## Step 4: Enable JitPack

1. Go to [JitPack.io](https://jitpack.io)
2. Enter your repository: `IMIS-Project/cnicscanner`
3. Click "Look up"
4. Select version `v1.0.0` and click "Get it"
5. Wait for the build to complete (green checkmark)

## Step 5: Verify Installation

Test the library in a sample project:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.IMIS-Project:cnicscanner:1.0.0")
}
```

## Step 6: Update Badge in README

Add JitPack badge:

```markdown
[![](https://jitpack.io/v/IMIS-Project/cnicscanner.svg)](https://jitpack.io/#IMIS-Project/cnicscanner)
```

## Publishing Updates

When you make changes and want to release a new version:

```powershell
# Make your changes
# ...

# Commit changes
git add .
git commit -m "Description of changes"
git push

# Create new version tag
git tag -a v1.0.1 -m "Version 1.0.1 - Bug fixes"
git push origin v1.0.1
```

JitPack will automatically build the new version.

## File Structure for Standalone Repository

```
cnicscanner/
├── build.gradle.kts (root)
├── settings.gradle.kts (root)
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
├── LICENSE
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── cnicscanner/  (library module)
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── consumer-rules.pro
    └── src/
        └── main/
            ├── AndroidManifest.xml
            └── java/com/sspa/cnicscanner/
                ├── CnicScanner.kt
                ├── core/
                │   └── ImageSource.kt
                ├── entities/
                │   └── CnicEntity.kt
                └── ocr/
                    └── CnicOcrParser.kt
```

## Troubleshooting

### Build Failed on JitPack

- Check the build log on JitPack
- Ensure all dependencies are available on Maven Central or Google's repository
- Verify `build.gradle.kts` has correct Android Gradle Plugin version
- Make sure `maven-publish` plugin is properly configured

### Version Not Showing Up

- Ensure you pushed the tag: `git push origin v1.0.0`
- Tags must start with 'v' for JitPack
- Wait a few minutes for JitPack to detect the new tag

### Cannot Resolve Dependency

- Add JitPack repository to `settings.gradle.kts`
- Check if the build succeeded on JitPack (green checkmark)
- Verify version number matches the tag

## Alternative: GitHub Packages

If you prefer GitHub Packages instead of JitPack:

1. Generate GitHub Personal Access Token with `write:packages` permission
2. Update `build.gradle.kts` publishing configuration
3. Users will need to authenticate to download

## Best Practices

1. **Semantic Versioning**: Use MAJOR.MINOR.PATCH (e.g., 1.0.0, 1.1.0, 2.0.0)
2. **Changelog**: Maintain a CHANGELOG.md file
3. **Documentation**: Keep README.md updated with examples
4. **Testing**: Include unit tests before releasing
5. **API Stability**: Avoid breaking changes in minor/patch versions
6. **License**: Include LICENSE file (Apache 2.0 recommended)

## Sample gradle.properties

```properties
# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

android.useAndroidX=true
android.enableJetifier=false

# Library info
GROUP=com.sspa
VERSION_NAME=1.0.0
```
