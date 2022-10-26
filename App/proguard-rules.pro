#-dontoptimize
#-dontobfuscate
-printusage
-ignorewarnings

#-keepclasseswithmembers public class MainKt {
#    public static void main(java.lang.String[]);
#}

#-dontwarn kotlinx.coroutines.debug.*
#-dontwarn org.graalvm.compiler.core.aarch64.*

#-keep class kotlin.** { *; }
#-keep class kotlinx.coroutines.** { *; }
#-keep class org.jetbrains.skia.** { *; }
#-keep class org.jetbrains.skiko.** { *; }
-keep class smol.** { *; }
#-keep class com.google.gson.** { *; }

#-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
#    void sourceInformation(androidx.compose.runtime.Composer,java.lang.String);
#    void sourceInformationMarkerStart(androidx.compose.runtime.Composer,int,java.lang.String);
#    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
#}