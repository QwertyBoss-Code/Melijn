package me.melijn.melijnbot.objects.translation

import me.melijn.melijnbot.database.DaoManager
import me.melijn.melijnbot.objects.command.CommandContext
import java.util.*

class Translateable(val path: String = "") {

    companion object {
        val defaultRecourseBundle: ResourceBundle = ResourceBundle.getBundle("strings")
        val dutchBelgianRecourseBundle: ResourceBundle = ResourceBundle.getBundle("strings", Locale("nl_BE"))
    }

    fun string(ctx: CommandContext): String {
        return string(ctx.daoManager, ctx.author.idLong, ctx.guild.idLong)
    }

    fun string(daoManager: DaoManager, userId: Long, guildId: Long = -1): String {
        val isSupporter = daoManager.supporterWrapper.supporterIds.contains(userId)
        return if (guildId > 0) {
            if (isSupporter)
                when (daoManager.userLanguageWrapper.languageCache.get(userId).get()) {
                    "nl_BE" -> dutchBelgianRecourseBundle.getString(path)
                    else -> guildString(daoManager, guildId)
                }
            else guildString(daoManager, guildId)
        } else {
            if (!isSupporter)
                defaultRecourseBundle.getString(path)
            else userString(daoManager, userId)
        }
    }

    private fun userString(daoManager: DaoManager, userId: Long): String {
        return when (daoManager.userLanguageWrapper.languageCache.get(userId).get()) {
            "nl_BE" -> dutchBelgianRecourseBundle.getString(path)
            else -> defaultRecourseBundle.getString(path)
        }
    }

    private fun guildString(daoManager: DaoManager, guildId: Long): String {
        return when (daoManager.guildLanguageWrapper.languageCache.get(guildId).get()) {
            "nl_BE" -> dutchBelgianRecourseBundle.getString(path)
            else -> defaultRecourseBundle.getString(path)
        }
    }

    fun default(): String = defaultRecourseBundle.getString(path)
}

