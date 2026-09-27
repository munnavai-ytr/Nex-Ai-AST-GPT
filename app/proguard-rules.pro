# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Room Database Entities and DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Moshi & JSON Data Models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class com.example.diagnostics.model.** { *; }
-keep class com.example.automation.agent.** { *; }
-keep class com.example.security.** { *; }
-keep class com.example.accessibility.model.** { *; }
-keep class com.example.memory.model.** { *; }
-keep class com.example.memory.entities.** { *; }
