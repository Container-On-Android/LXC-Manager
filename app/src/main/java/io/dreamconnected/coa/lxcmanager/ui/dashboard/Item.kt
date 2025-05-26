package io.dreamconnected.coa.lxcmanager.ui.dashboard

data class Item(
    val name: String,           // 容器名称
    val state: String,          // 容器状态（如 RUNNING, STOPPED）
    val autostart: String,      // 自动启动（0 或 1）
    val groups: String,         // GROUPS
    val ipv4: String?,          // IPv4 地址，可能为空
    val ipv6: String?,          // IPv6 地址，可能为空
    val unprivileged: String    // 是否为非特权容器
)
