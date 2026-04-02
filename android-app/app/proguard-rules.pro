# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Compose
-keep class androidx.compose.** { *; }

# libsu (root access)
-keep class com.topjohnwu.superuser.** { *; }
