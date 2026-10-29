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
