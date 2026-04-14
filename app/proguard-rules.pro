# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep YukiHook classes
-keep class com.highcapable.yukihookapi.** { *; }
-keep class com.highcapable.yukihookapi.hook.** { *; }

# Keep AutoNFC classes  
-keep class com.gswxxn.autonfc.** { *; }

# Keep Google Play Service
-keep class com.google.android.gms.** { *; }
