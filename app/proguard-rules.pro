# SQLCipher native bindings — must not be stripped.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Room entities and database classes accessed via reflection by the runtime.
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Kotlin metadata used by reflection-aware libraries.
-keep class kotlin.Metadata { *; }

# Hardening §20.4: do not preserve toString() implementations of model classes
# that could be invoked by accidental logging. Our overrides return only ids,
# but R8 may inline — accept that risk and rely on Logger no-op + reviewers.
