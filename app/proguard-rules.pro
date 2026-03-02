# -- General --
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# -- Android & AndroidX --
# Removed broad keep rules to allow shrinking

# -- OkHttp (used transitively) --
-dontwarn okhttp3.**
-dontwarn okio.**

# -- Gson --
-keep class com.google.gson.** { *; }

# -- Firebase --
-keep class com.google.firebase.** { *; }

# -- Agora --
# Keep Agora classes to prevent native crashes
-keep class io.agora.** { *; }

# -- Cloudinary --
-keep class com.cloudinary.** { *; }

# -- Picasso (transitive dependency of Cloudinary) --
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**

# -- Desugar (for ThrowableExtension) --
-keep class com.google.devtools.build.android.desugar.runtime.ThrowableExtension { *; }
-dontwarn com.google.devtools.build.android.desugar.runtime.ThrowableExtension

# -- Razorpay --
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**

# -- App Specific --
# Keep Data Models (Critical for Firestore/Gson serialization)
-keep class com.example.taxconnect.data.models.** { *; }

# Keep Application class
-keep class com.example.taxconnect.MyApplication { *; }
