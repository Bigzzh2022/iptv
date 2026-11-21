# NanoHTTPD 通过反射/接口回调调用，保留全部成员
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# MMKV JNI 绑定
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# OkHttp 可选依赖
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 保留枚举与注解，Room / Media3 反射需要
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
