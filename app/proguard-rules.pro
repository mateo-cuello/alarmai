# Proguard rules for AlarmAI.
# Add project-specific Proguard rules here.
# By default, the active rules are in the Android SDK's proguard-android-optimize.txt.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep model / serialized JSON classes intact
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Gemini SDK components if needed
-keep class com.google.ai.client.generativeai.** { *; }
