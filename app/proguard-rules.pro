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
