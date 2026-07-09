# ProGuard rules for Cloudflare Manager
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
