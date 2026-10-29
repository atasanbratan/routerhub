# Extensions are compiled against these packages by NAME and link to the host's copies at runtime
# (see ExtensionClassLoader.SHARED_PACKAGES). R8 must neither rename nor strip them, otherwise
# extensions fail with NoClassDefFoundError / NoSuchMethodError / AbstractMethodError.
-keep class dev.routerhub.api.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# Optional TLS providers OkHttp probes for reflectively.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Play build: the bundled extensions use JSch, which creates its ciphers, key exchanges and signature
# algorithms from class names in its configuration, and jsoup. Their optional integrations (logging
# frameworks, Bouncy Castle, JNA) are not on the classpath.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.newsclub.net.unix.**
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
