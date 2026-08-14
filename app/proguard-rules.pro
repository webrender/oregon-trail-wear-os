# kotlinx.serialization ships its own consumer rules, which cover the generated
# serializers for every @Serializable class. They rely on the class and its synthetic
# `Companion.serializer()` surviving, and R8 in `-optimize` mode is happy to rewrite
# both. Keeping the save model explicitly is cheaper than debugging a release build
# whose only symptom is that every existing run fails to load.
-keepclassmembers class com.oregontrail.wear.** {
    *** Companion;
}
-keepclasseswithmembers class com.oregontrail.wear.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose keeps no reflection of its own, but the ART profile shipped in the APK
# addresses methods by name — see `androidx.profileinstaller` in build.gradle.kts.
# Renaming them is fine (the profile is transformed alongside), but stripping the
# line-number table makes a release stack trace unreadable for no size win worth having.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
