# Compose and DataStore ship their own consumer rules.
# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# play review-ktx references this annotation from play-services-basement, which may not be on the classpath. It's
# only an annotation, so nothing breaks when it's absent.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
