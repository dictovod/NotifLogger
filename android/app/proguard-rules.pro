# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep notification listener service
-keep class com.notiflogger.app.NotificationLoggerService { *; }

# Keep activation manager
-keep class com.notiflogger.app.ActivationManager { *; }

# Keep JSON parsing for logs
-keepclassmembers class * {
    @org.json.* <fields>;
}

# Keep classes used with reflection
-keepclassmembers class com.notiflogger.app.** {
    public <init>(...);
}

# Android specific
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }

# Keep serialization
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}