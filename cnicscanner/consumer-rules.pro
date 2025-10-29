# Consumer ProGuard rules for cnicscanner library

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep public API
-keep public class com.sspa.cnicscanner.** { *; }
