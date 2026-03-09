# Attendance ProGuard Rules

# Hilt / Dagger
-keepattributes *Annotation*
-keepattributes Signature
-keep class dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }

# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class **$serializer {
    public static ** INSTANCE;
}

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# General Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.Unit

# Keep entities and models
-keep class sg.org.bcc.attendance.data.local.entities.** { *; }
-keep class sg.org.bcc.attendance.data.remote.** { *; }
-keep class sg.org.bcc.attendance.util.qr.QrInfo { *; }

# Google API Client
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.sheets.v4.** { *; }
-keep interface com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn com.google.errorprone.annotations.**

# Preserving fields used for JSON serialization (needed for GoogleClientSecrets)
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# Explicitly keep GoogleClientSecrets and its inner classes
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets { *; }
-keep class com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets$Details { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class com.google.api.client.json.gson.** { *; }
-dontwarn com.google.gson.**
