# Room, Hilt, Compose, Glance, Navigation 3 ship their own consumer rules.
# The one thing R8 can't infer is kotlinx.serialization reflection.

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep generated serializers and the companion serializer() accessor.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }

# Our @Serializable types: Nav3 back-stack keys and the JSON backup model.
-keep @kotlinx.serialization.Serializable class com.victorfalcon.dose.** { *; }
