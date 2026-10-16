# NewsHub R8 rules (used only if minifyEnabled is switched on in app/build.gradle).

# Room entities / DAOs are generated and referenced reflectively by Room.
-keep class com.newshub.app.data.db.** { *; }

# Retrofit service interface (method signatures are read at runtime).
-keep,allowobfuscation interface com.newshub.app.data.net.FeedApi
-keepattributes Signature, *Annotation*, Exceptions

# jsoup optional integrations that are not on the classpath.
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.annotations.**

# OkHttp optional TLS providers.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
