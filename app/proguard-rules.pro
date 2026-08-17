# Proguard rules for AuraView

# Keep all App Data Models & AI Client Payloads
-keep class com.arora.assistant.core.ai.** { *; }
-keep class com.arora.assistant.core.media.** { *; }
-keep class com.arora.assistant.core.data.** { *; }
-keep class com.arora.assistant.core.agent.** { *; }
-keep class com.arora.assistant.ui.miniapps.** { *; }

# Shizuku Wireless ADB API
-keep class dev.rikka.shizuku.** { *; }
-dontwarn dev.rikka.shizuku.**

# ML Kit Text Recognition & Translation
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
