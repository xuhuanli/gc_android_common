# --- SPI / ServiceLoader ---
# Keep provider implementations discoverable by ServiceLoader.
-keep class * implements com.igancao.hpt_jsbridge.BridgeModuleProvider {
    public <init>();
}
