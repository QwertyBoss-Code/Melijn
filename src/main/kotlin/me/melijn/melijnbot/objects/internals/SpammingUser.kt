package me.melijn.melijnbot.objects.internals

data class SpammingUser(
    val userId: Long,
    val startTime: Long,
    var count: Short
)