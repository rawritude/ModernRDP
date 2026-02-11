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

# Keep CertificateInfo (used in JNI callbacks)
-keep class com.modernrdp.rdp.CertificateInfo { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep Compose stability annotations for better performance
-keep class androidx.compose.runtime.** { *; }

# Biometric
-keep class androidx.biometric.** { *; }

# WakeOnLan (uses reflection-safe patterns, but keep for safety)
-keep class com.modernrdp.util.WakeOnLan { *; }

# Keep data classes used in Compose state
-keep class com.modernrdp.ui.screens.session.UiEvent { *; }
-keep class com.modernrdp.ui.screens.session.UiEvent$* { *; }
-keep class com.modernrdp.ui.screens.settings.SettingsState { *; }
-keep class com.modernrdp.ui.screens.editor.EditorState { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
