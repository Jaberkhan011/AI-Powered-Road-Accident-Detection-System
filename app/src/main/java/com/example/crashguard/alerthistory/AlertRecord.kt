package com.example.crashguard.alerthistory

data class AlertRecord(
    val id: Long,
    val type: String, // "Manual" or "Automatic"
    val time: String,
    val location: String
)