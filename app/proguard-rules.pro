# Add project specific ProGuard rules here.

# Retrofit — keep service interfaces AND their method annotations
-keep interface com.singam.lionlibrary.data.remote.api.TmdbApiService { *; }

# Keep Retrofit annotation metadata
-keepattributes Signature
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.singam.lionlibrary.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.singam.lionlibrary.** {
    *;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil
-dontwarn coil.**
