# Add project specific ProGuard rules here.

# Keep Retrofit service interfaces
-keep,allowobfuscation interface com.example.mediahub.data.remote.api.TmdbApiService

# Keep serializable DTOs (kotlinx.serialization)
-keepclassmembers @kotlinx.serialization.Serializable class com.example.mediahub.** {
    *;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
