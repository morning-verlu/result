# Keep line numbers for easier crash de-obfuscation
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod,InnerClasses

# Ktor / kotlinx serialization / Supabase models
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-dontwarn kotlinx.serialization.**
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# Compose runtime metadata that can be stripped too aggressively by R8
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# SQLDelight generated database models and adapters
-keep class cn.verlu.cloud.db.** { *; }
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**
