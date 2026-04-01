# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Room entities
-keep class es.bercianor.tocacorrer.data.local.entity.** { *; }

# Keep Kotlin metadata
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
