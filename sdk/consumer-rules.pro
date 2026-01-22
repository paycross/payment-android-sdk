# Keep Retrofit DTOs
-keep class com.paycross.sdk.internal.api.models.** { *; }

# Keep result classes (passed via Intent)
-keep class com.paycross.sdk.PayCrossResult { *; }
-keep class com.paycross.sdk.PayCrossResult$* { *; }
-keep enum com.paycross.sdk.Recovery { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
