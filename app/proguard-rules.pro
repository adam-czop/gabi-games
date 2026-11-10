# kotlinx.serialization, Koin, Room and Compose ship their own consumer rules.
# Keep @Serializable navigation routes: their serializers are looked up reflectively by Navigation.
-keep @kotlinx.serialization.Serializable class com.familygames.** { *; }
-keepclassmembers class com.familygames.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Release builds never log (Timber is planted only in debug).
-assumenosideeffects class timber.log.Timber* {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
