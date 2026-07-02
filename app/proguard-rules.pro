# Keep local JSON persistence stable if R8/minification is enabled for release.
# Gson reflects over model field names for the current SharedPreferences store.
-keep class edu.bnbu.student.mvp.core.model.** { *; }
-keep class edu.bnbu.student.mvp.core.local.** { *; }

# Keep network request/response models — Gson serialization relies on reflection
-keep class edu.bnbu.student.mvp.core.network.** { *; }

# Keep OkHttp internals (connection pool, HTTP/2, interceptors)
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Android Keystore encryption classes
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
