-keep class com.paycross.sdk.internal.api.models.** { *; }

-keep class com.paycross.sdk.PayCrossResult { *; }
-keep class com.paycross.sdk.PayCrossResult$* { *; }
-keep enum com.paycross.sdk.Recovery { *; }
# Parcelize reads PendingReason back with valueOf, which R8 renaming would break.
-keep enum com.paycross.sdk.PendingReason { *; }

# Retrofit reads generic signatures and annotations at runtime.
-keepattributes Signature
-keepattributes *Annotation*
