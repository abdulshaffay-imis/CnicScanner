# Changelog

All notable changes to the CNIC Scanner library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.1] - 2025-11-10

### Changed
- Removed Jetpack Compose dependencies to reduce library size
- Removed sample UI components (SampleCnicScannerActivity and SampleCnicScannerScreen)
- Library now focuses solely on core CNIC scanning functionality

### Improved
- Enhanced `cardType` field detection with fuzzy matching for OCR errors
- Better handling of text recognition issues like "Nationa Ldenity ard"
- Added flexible regex pattern for "National Identity Card" variations
- Implemented similarity-based fallback detection

### Technical
- Reduced library size by removing Compose BOM and related dependencies
- Removed Coil image loading dependency
- Cleaner library structure focusing on core functionality

## [1.1.0] - 2025-11-10

### Added
- New `cardType` field to capture "National Identity Card" text from CNIC
- Sample CNIC scanner screen with Jetpack Compose UI
- Sample activity demonstrating library usage
- Compose-based UI components for easier integration

### Enhanced
- Updated `CnicEntity` with `cardType` field
- Enhanced OCR parser to extract card type information
- Improved toString() method to include cardType

## [1.0.0] - 2025-10-29

### Added
- Initial release of CNIC Scanner library
- Support for scanning Pakistani CNIC cards using ML Kit
- Three image capture methods:
  - Camera capture with document scanner
  - Gallery image selection
  - Enhanced ML Kit Document Scanner mode
- OCR processing using Google ML Kit Text Recognition
- Customizable parsing through `CnicOcrParser` interface
- Support for both front and back CNIC scanning
- Complete CNIC data extraction:
  - CNIC number
  - Name
  - Father's name
  - Date of birth
  - Issue and expiry dates
  - Gender
  - Country
  - Addresses (front and back)
- Kotlin Coroutines support for async operations
- Sample parser implementation (`SampleCnicOcrParser`)
- Comprehensive documentation:
  - README with complete API reference
  - Quick start guide
  - Publishing instructions
  - Code examples
- Apache 2.0 License
- ProGuard rules for release builds
- Consumer ProGuard rules for library users

### Technical Details
- Min SDK: 26 (Android 8.0)
- Target SDK: 34
- Compile SDK: 35
- Kotlin: 2.0.21
- Java: 11 compatibility

### Dependencies
- AndroidX Core KTX 1.15.0
- AndroidX Activity KTX 1.9.3
- Kotlin Coroutines 1.9.0
- ML Kit Text Recognition 16.0.1
- ML Kit Document Scanner 16.0.0-beta1

### Documentation
- Complete README.md with usage examples
- QUICKSTART.md for fast integration
- PUBLISHING.md for GitHub/JitPack publishing
- Sample implementation included
- KDoc comments throughout codebase

### Build
- Gradle build script with Maven publishing
- JitPack compatible configuration
- ProGuard/R8 optimization ready

---

## [Unreleased]

### Planned Features
- Unit tests for core functionality
- Instrumented tests for Android components
- Demo application
- CI/CD pipeline integration
- Additional language support
- Enhanced error handling
- Retry mechanisms
- Image preprocessing options
- Batch scanning support

---

## Version History

| Version | Date | Summary |
|---------|------|---------|
| 1.0.0 | 2025-10-29 | Initial release with core CNIC scanning functionality |

---

## Upgrade Guide

### From Local Implementation to Library

If you're migrating from the local `cnicscanner` package to this library:

1. Update imports:
```kotlin
// Old
import com.sspa.sspaapp.domain.entities.CnicEntity

// New
import com.sspa.cnicscanner.entities.CnicEntity
import com.sspa.cnicscanner.CnicScanner
import com.sspa.cnicscanner.core.ImageSource
import com.sspa.cnicscanner.ocr.CnicOcrParser
```

2. Update dependency:
```kotlin
dependencies {
    implementation("com.github.IMIS-Project:cnicscanner:1.0.0")
}
```

3. No API changes - same interface and usage patterns

---

For detailed changes and migration guides, see the [README](README.md).
