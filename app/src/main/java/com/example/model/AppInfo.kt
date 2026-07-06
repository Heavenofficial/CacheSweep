package com.example.model

data class AppInfo(
    val packageName: String,
    val appName: String,
    val cacheSize: Long, // Size in bytes
    val isWhitelisted: Boolean
)
