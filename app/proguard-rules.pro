-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

-keep class app.oneclick.vpn.vpn.OneClickVpnService { *; }
-keep class app.oneclick.vpn.vpn.WgController { *; }
-keep class app.oneclick.vpn.vpn.TunnelState { *; }
-keep interface app.oneclick.vpn.vpn.TunnelController { *; }

# Preserve Kotlin coroutines metadata used by Flow collection.
-keepclassmembers class kotlinx.coroutines.** { *; }
