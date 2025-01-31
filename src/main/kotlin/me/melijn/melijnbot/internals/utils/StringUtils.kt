package me.melijn.melijnbot.internals.utils

import com.wrapper.spotify.Base64
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.command.PLACEHOLDER_PREFIX
import java.nio.ByteBuffer
import kotlin.math.pow

val SPACE_PATTERN = Regex("\\s+")

object StringUtils {
    private val backTicks = "```".toRegex()
    fun splitMessageWithCodeBlocks(
        message: String,
        nextSplitThreshold: Int = 1800,
        maxLength: Int = 1970,
        lang: String = ""
    ): List<String> {
        var msg = message
        val messages = ArrayList<String>()
        var shouldAppendBackTicks = false
        var shouldPrependBackTicks = false
        while (msg.length > maxLength) {
            var findLastNewline = msg.substring(0, maxLength - 1)
            if (shouldPrependBackTicks) {
                findLastNewline = "```$lang\n$findLastNewline"
            } else {
                shouldPrependBackTicks = true
            }

            if (findLastNewline.contains("```")) {

                val triple = getBackTickAmountAndLastTwoIndexes(findLastNewline)

                val amount = triple.first
                val previousIndex = triple.second
                val mostRightIndex = triple.third
                val lastEvenIndex = if (amount % 2 == 0) mostRightIndex else previousIndex

                shouldPrependBackTicks = true

                if (lastEvenIndex > nextSplitThreshold) {
                    val subMsg = msg.substring(0, lastEvenIndex)
                    messages.add(subMsg)
                    msg = msg.substring(lastEvenIndex)
                    shouldAppendBackTicks = false
                    continue
                } else {
                    shouldAppendBackTicks = true
                }
            }

            val index = getSplitIndex(findLastNewline, nextSplitThreshold, maxLength)
            messages.add(
                findLastNewline.substring(0, index) + (if (shouldAppendBackTicks) "```" else "")
            )

            msg = msg.substring(index)
        }

        if (shouldPrependBackTicks) {
            msg = "```$lang\n$msg"
        }

        if (msg.isNotEmpty()) messages.add(msg)
        return messages
    }

    fun humanReadableByteCountBin(bytes: Int): String = humanReadableByteCountBin(bytes.toLong())
    fun humanReadableByteCountBin(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 0xfffccccccccccccL shr 40 -> String.format("%.3f KiB", bytes / 2.0.pow(10.0))
            bytes < 0xfffccccccccccccL shr 30 -> String.format("%.3f MiB", bytes / 2.0.pow(20.0))
            bytes < 0xfffccccccccccccL shr 20 -> String.format("%.3f GiB", bytes / 2.0.pow(30.0))
            bytes < 0xfffccccccccccccL shr 10 -> String.format("%.3f TiB", bytes / 2.0.pow(40.0))
            bytes < 0xfffccccccccccccL -> String.format("%.3f PiB", (bytes shr 10) / 2.0.pow(40.0))
            else -> String.format("%.3f EiB", (bytes shr 20) / 2.0.pow(40.0))
        }
    }

    private fun getBackTickAmountAndLastTwoIndexes(findLastNewline: String): Triple<Int, Int, Int> {
        var amount = 0
        var almostMostRight = 0
        var mostRight = 0

        for (result in backTicks.findAll(findLastNewline)) {
            amount++
            almostMostRight = mostRight
            mostRight = result.range.last + 1
        }
        return Triple(amount, almostMostRight, mostRight)
    }

    fun splitMessage(message: String, splitAtLeast: Int = 1800, maxLength: Int = 2000): List<String> {
        var msg = message
        val messages = ArrayList<String>()
        while (msg.length > maxLength) {
            val findLastNewline = msg.substring(0, maxLength - 1)

            val index = getSplitIndex(findLastNewline, splitAtLeast, maxLength)

            messages.add(msg.substring(0, index))
            msg = msg.substring(index)
        }
        if (msg.isNotEmpty()) messages.add(msg)
        return messages
    }

    private fun getSplitIndex(findLastNewline: String, splitAtLeast: Int, maxLength: Int): Int {
        var index = findLastNewline.lastIndexOf("\n")
        if (index < splitAtLeast) {
            index = findLastNewline.lastIndexOf(". ")
        }
        if (index < splitAtLeast) {
            index = findLastNewline.lastIndexOf(" ")
        }
        if (index < splitAtLeast) {
            index = findLastNewline.lastIndexOf(",")
        }
        if (index < splitAtLeast) {
            index = maxLength - 1
        }

        return index
    }

    fun Long.toBase64(): String {
        return Base64.encode(
            ByteBuffer
                .allocate(Long.SIZE_BYTES)
                .putLong(this)
                .array()
        )
            .remove("=")
    }
}

fun boolFromStateArg(state: String): Boolean? {
    return when (state) {
        "disable", "no", "false", "disabled", "off" -> false
        "enable", "yes", "true", "enabled", "on" -> true
        else -> null
    }
}

fun String.remove(vararg strings: String, ignoreCase: Boolean = false): String {
    var newString = this
    for (string in strings) {
        newString = newString.replace(string, "", ignoreCase)
    }
    return newString
}

fun String.removeFirst(vararg strings: String, ignoreCase: Boolean = false): String {
    var newString = this
    for (string in strings) {
        newString = newString.replaceFirst(string, "", ignoreCase)
    }
    return newString
}

fun String.removeFirst(vararg regexes: Regex): String {
    var newString = this
    for (regex in regexes) {
        newString = newString.replaceFirst(regex, "")
    }
    return newString
}

fun String.removePrefix(prefix: CharSequence, ignoreCase: Boolean = false): String {
    if (startsWith(prefix, ignoreCase)) {
        return substring(prefix.length)
    }
    return this
}

fun String.splitIETEL(delimiter: String): List<String> {
    val res = this.split(delimiter)
    return if (res.first().isEmpty() && res.size == 1) {
        emptyList()
    } else {
        res
    }
}

fun String.withVariable(toReplace: String, obj: Any): String {
    return this.replace("%$toReplace%", obj.toString())
}

fun String.escapeCodeblockMarkdown(andDiscordInvite: Boolean = false): String {
    val replaced = this
        .replace("`", "'")
    return if (andDiscordInvite) replaced.escapeDiscordInvites()
    else replaced
}

fun String.escapeMarkdown(): String {
    return this.replace("*", "\\*")
        .replace("||", "\\|\\|")
        .replace("_", "\\_")
        .replace("~~", "\\~\\~")
        .replace("> ", "\\> ")
        .replace("`", "'")
}

fun String.escapeDiscordInvites(): String {
    return this.replace("discord.gg/", " yourFailedInviteLink ", ignoreCase = true)
        .replace("discord.com/invite", " yourFailedInviteLink ", ignoreCase = true)
        .replace("discordapp.com/invite", " yourFailedInviteLink ", ignoreCase = true)
        .replace("discord.media/invite", " yourFailedInviteLink ", ignoreCase = true)
}

// IC == In CodeBlock
fun String.withSafeVarInCodeblock(toReplace: String, obj: Any, escapeInvites: Boolean = false): String {
    return this.replace(
        "%$toReplace%",
        obj.toString().escapeCodeblockMarkdown().run {
            if (escapeInvites) this.escapeDiscordInvites()
            else this
        }
    )
}

fun String.withSafeVariable(toReplace: String, obj: Any): String {
    return this.replace(
        "%$toReplace%",
        obj.toString()
            .escapeMarkdown()
            .escapeDiscordInvites()
    )
}

fun String.toUpperWordCase(): String {
    var previous = ' '
    var newString = ""
    this.toCharArray().forEach { c: Char ->
        newString += if (previous == ' ') c.uppercase() else c.lowercase()
        previous = c
    }
    return newString
}

fun String.replacePrefix(context: ICommandContext): String {
    return this.withVariable(PLACEHOLDER_PREFIX, context.usedPrefix)
}

fun String.replacePrefix(prefix: String): String {
    return this.withVariable(PLACEHOLDER_PREFIX, prefix)
}

fun Int.toHexString(size: Int = 6): String {
    return String.format("#%0${size}X", 0xFFFFFF and this)
}

fun String.isInside(vararg stringList: String, ignoreCase: Boolean): Boolean {
    return stringList.any { it.equals(this, ignoreCase) }
}

fun String.isInside(stringList: Collection<String>, ignoreCase: Boolean): Boolean {
    return stringList.any { it.equals(this, ignoreCase) }
}