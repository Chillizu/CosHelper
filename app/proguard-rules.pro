# Keep native method declarations and their classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI classes
-keep class com.coshelper.stt.WhisperJNI { <methods>; }
-keep class com.coshelper.audio.OpusCodec { <methods>; }

# Application and components declared in the manifest
-keep class com.coshelper.CosHelperApplication { *; }
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.accessibilityservice.AccessibilityService

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Compose runtime generated lambdas
-keepclassmembers class * extends androidx.compose.runtime.internal.ComposableLambdaImpl { *; }

# Preserve Kotlin metadata and generic signatures
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep serialization methods used by some libraries
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
