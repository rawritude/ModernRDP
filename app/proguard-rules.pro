# FreeRDP JNI - keep native method bindings
-keepclassmembers class com.modernrdp.rdp.FreeRdpBridge {
    native <methods>;
}
-keepclassmembers class com.modernrdp.rdp.FreeRdpBridge$* {
    *;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
