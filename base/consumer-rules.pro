# base 模块对使用方的 R8/ProGuard 规则（随 AAR 打包，消费方自动继承）。
#
# 主要保护 kotlinx.serialization 生成的 serializer 与 Ktor 依赖的元数据：
# 这些类型由编译器插件生成，R8 在没有规则时可能裁掉 KSerializer 伴生对象，
# 导致运行时 "Serializer for class X is not found"。

-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# 保留所有 @Serializable 类的伴生对象与 serializer 方法
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留本模块（及未来业务包）生成的 serializer 与 $serializer 类
-keep,includedescriptorclasses class com.foolproof.**$$serializer { *; }
-keep,includedescriptorclasses class com.foolproof.**$*Serializer { *; }

# kotlinx.serialization 运行时不要警告/裁剪
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
