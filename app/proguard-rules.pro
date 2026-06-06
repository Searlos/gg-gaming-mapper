# Keep WebView JavaScript interface
-keepclassmembers class com.gg.gaming.mapper.** {
    @android.webkit.JavascriptInterface public *;
}
-keepattributes JavascriptInterface

# Keep all classes
-keep class com.gg.gaming.mapper.** { *; }

# Keep Kotlin
-keepattributes *Annotation*, Signature, Exception

# Gson
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }