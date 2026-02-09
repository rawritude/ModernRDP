package com.modernrdp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class RdpConnection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val hostname: String = "",
    val port: Int = 3389,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    // Display settings
    val resolutionMode: ResolutionMode = ResolutionMode.MATCH_DEVICE,
    val customWidth: Int = 1920,
    val customHeight: Int = 1080,
    val colorDepth: Int = 32,
    // Performance
    val enableWallpaper: Boolean = false,
    val enableFontSmoothing: Boolean = true,
    val enableFullWindowDrag: Boolean = false,
    // Security
    val useTls: Boolean = true,
    val useNla: Boolean = true,
    // Gateway
    val gatewayHostname: String = "",
    val gatewayPort: Int = 443,
    val gatewayUsername: String = "",
    val gatewayPassword: String = "",
    // Metadata
    val lastConnected: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class ResolutionMode {
    MATCH_DEVICE,
    CUSTOM,
    FIT_SCREEN,
}
