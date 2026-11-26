# sherpa-onnx talks to native code by name
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class * { native <methods>; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.pagewhisper.app.**$$serializer { *; }
-keepclassmembers class com.pagewhisper.app.** { *** Companion; }
-keepclasseswithmembers class com.pagewhisper.app.** { kotlinx.serialization.KSerializer serializer(...); }
# jsoup / okhttp optional dependencies
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
# PDFBox (PDF books): optional image decoders it can use are not bundled
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }
