# Keep Retrofit DTOs
-keep class com.paycross.sdk.internal.api.models.** { *; }

# Keep result classes (passed via Intent)
-keep class com.paycross.sdk.PayCrossResult { *; }
-keep class com.paycross.sdk.PayCrossResult$* { *; }
-keep enum com.paycross.sdk.Recovery { *; }
# Top-level, so PayCrossResult$* above does not cover it. Parcelize writes
# an enum property as its name and reads it back with valueOf, which R8
# renaming would break across the activity boundary.
-keep enum com.paycross.sdk.PendingReason { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
