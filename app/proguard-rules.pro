# Room, Hilt and WorkManager ship their own consumer rules; these cover the rest.

# SQLCipher uses JNI and must keep its native-facing classes.
-keep class net.zetetic.database.** { *; }
-keep,includedescriptorclasses class net.zetetic.database.sqlcipher.** { *; }

# Strip android.util.Log calls in release so SMS bodies can never be logged.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
