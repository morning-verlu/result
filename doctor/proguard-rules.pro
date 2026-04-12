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

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes SourceFile,LineNumberTable

# ZXing（Sync 扫码登录二维码生成 MultiFormatWriter 等）
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# 本草 API DTO（kotlinx.serialization）
-keep class cn.verlu.doctor.data.herb.dto.** { *; }

# Room 实体（含收藏）
-keep class cn.verlu.doctor.data.local.herb.** { *; }