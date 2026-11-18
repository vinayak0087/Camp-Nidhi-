# CampNidhi Release ProGuard / R8 Rules
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Preserve Firestore POJOs & data models for reflection serialization
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** {
    public *;
    <fields>;
    <init>(...);
}

# Preserve Firebase & Google Credential Manager / Identity classes
-keepnames class com.google.firebase.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
