# Attendance ProGuard Rules

# General Project Packages
-keep class sg.org.bcc.attendance.data.local.entities.** { *; }
-keep class sg.org.bcc.attendance.data.remote.** { *; }
-keep class sg.org.bcc.attendance.data.repository.** { *; }
-keep class sg.org.bcc.attendance.sync.** { *; }
-keep class sg.org.bcc.attendance.util.** { *; }

# Application Class
-keep class sg.org.bcc.attendance.AttendanceApp { *; }

# Hilt / Dagger
-keepattributes *Annotation*
-keepattributes Signature
-keep class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
}

# Room
-dontwarn androidx.room.paging.**
-keep class * {
    @androidx.room.Entity *;
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.TypeConverter *;
}

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class **$serializer {
    public static ** INSTANCE;
}
-keep class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**

# Ktor & OkHttp
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# General Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.Unit

# Google API Client & Sheets
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.sheets.v4.** { *; }
-keep interface com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn com.google.errorprone.annotations.**

# Missing Java SE classes referenced by Apache/Google libs
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn javax.servlet.**
-dontwarn org.apache.avalon.**
-dontwarn org.apache.log.**
-dontwarn org.apache.log4j.**
-dontwarn org.joda.time.**
-dontwarn org.apache.commons.logging.**

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

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-dontwarn androidx.camera.view.**

# ML Kit & Firebase (Component Discovery)
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar
