# -- General --
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# -- Android & AndroidX --
# Removed broad keep rules to allow shrinking

# -- Retrofit & OkHttp --
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# -- Gson --
-keep class com.google.gson.** { *; }

# -- Firebase --
-keep class com.google.firebase.** { *; }

# -- Agora --
# Keep Agora classes to prevent native crashes
-keep class io.agora.** { *; }

# -- Cloudinary --
-keep class com.cloudinary.** { *; }

# -- Razorpay --
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**

# -- App Specific --
# Keep Data Models (Critical for Firestore/Gson serialization)
-keep class com.example.taxconnect.model.** { *; }

# Keep Network Interfaces (Retrofit)
-keep class com.example.taxconnect.network.** { *; }

# Keep Application class
-keep class com.example.taxconnect.MyApplication { *; }
