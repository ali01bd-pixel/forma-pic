# FORMA PIC ProGuard / R8 Configuration
-keepattributes *Annotation*,InnerClasses,Signature,ExifInterface
-keep class com.ali.formapic.domain.model.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }
