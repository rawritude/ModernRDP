# FreeRDP JNI - keep native method bindings
-keepclassmembers class com.modernrdp.rdp.FreeRdpBridge {
    native <methods>;
}

# Keep all static JNI callback methods (called from native code via reflection)
-keepclassmembers class com.modernrdp.rdp.FreeRdpBridge$Companion {
    public static void onNative*(...);
    public static int onNative*(...);
    public static boolean onNative*(...);
}

# Keep the companion object itself
-keep class com.modernrdp.rdp.FreeRdpBridge$Companion { *; }

# Keep SessionState enum (referenced from native callbacks)
-keep class com.modernrdp.rdp.SessionState { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
