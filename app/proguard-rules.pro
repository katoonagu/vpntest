-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

-keep class app.oneclick.vpn.vpn.OneClickVpnService { *; }
-keep class app.oneclick.vpn.vpn.WgController { *; }

# Preserve Kotlin coroutines metadata used by Flow collection.
-keepclassmembers class kotlinx.coroutines.** { *; }
