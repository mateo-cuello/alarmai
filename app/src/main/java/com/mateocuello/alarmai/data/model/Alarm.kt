package com.mateocuello.alarmai.data.model

data class Alarm(
    val hour: Int,
    val minute: Int,
    val isActive: Boolean = false,
    val label: String = "Wake Up!"
)
