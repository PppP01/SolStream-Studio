# 1. Keep all Native JNI methods & classes in SolStream Studio (RNNoise C Bridge)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.solgaming.solstreamstudio.SolNativeRnnoise { *; }
-keep class com.solgaming.solstreamstudio.MicDspProcessor { *; }
-keep class com.solgaming.solstreamstudio.SolDualAudioEngine { *; }

# 2. Keep Pedro's RootEncoder & RTMP/RTMPS classes (including reflection telemetry hooks)
-keep class com.pedro.** { *; }
-dontwarn com.pedro.**
-dontwarn io.ktor.**
-dontwarn kotlinx.**

# 3. Keep Android 10-18 Service & AccessibilityService entry points for Play Store .aab / R8
-keep public class * extends android.app.Service
-keep public class * extends android.accessibilityservice.AccessibilityService

# 4. Strip verbose debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
