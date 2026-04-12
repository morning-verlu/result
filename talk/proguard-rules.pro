# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name for the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# release 扫码（CameraX + ML Kit）：与 Sync app 模块同款；R8 过度优化易导致 Tasks 回调 / 原生桥接处崩溃
-keepattributes SourceFile,LineNumberTable
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.video.**

-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.odml.** { *; }

-keep class com.google.android.gms.tasks.** { *; }
-keep interface com.google.android.gms.tasks.OnCompleteListener { *; }
-keep interface com.google.android.gms.tasks.OnSuccessListener { *; }
-keep interface com.google.android.gms.tasks.OnFailureListener { *; }

# ZXing（二维码生成 MultiFormatWriter 等；部分实现靠反射）
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
