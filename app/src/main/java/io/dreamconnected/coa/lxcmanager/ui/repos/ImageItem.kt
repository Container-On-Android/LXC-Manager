package io.dreamconnected.coa.lxcmanager.ui.repos

data class ImageItem(
    val distribution: String,
    val release: String,
    val architecture: String,
    val variant: String,
    val fullPath: String,
    val downloadUrl: String
)