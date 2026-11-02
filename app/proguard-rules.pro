-keep class com.pedro.** { *; }
-dontwarn com.pedro.**
-dontwarn io.ktor.**
-dontwarn kotlinx.**

-keepclasseswithmembernames class * {
    native <methods>;
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
