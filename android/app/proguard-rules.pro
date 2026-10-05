# CRM Phone Agent release shrinking rules.
# OkHttp/Okio ship their own consumer rules; nothing else here uses reflection.
-dontwarn okhttp3.**
-dontwarn okio.**

# security-crypto pulls in Google Tink, which references error-prone's build-time-only
# annotations (@CanIgnoreReturnValue etc.) - never present, never needed at runtime.
-dontwarn com.google.errorprone.annotations.**
