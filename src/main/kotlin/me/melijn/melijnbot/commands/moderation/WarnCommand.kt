package me.melijn.melijnbot.commands.moderation

import me.melijn.melijnbot.commandutil.moderation.ModUtil
import me.melijn.melijnbot.database.warn.Warn
import me.melijn.melijnbot.enums.LogChannelType
import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.translation.PLACEHOLDER_USER
import me.melijn.melijnbot.internals.translation.i18n
import me.melijn.melijnbot.internals.utils.*
import me.melijn.melijnbot.internals.utils.checks.getAndVerifyLogChannelByType
import me.melijn.melijnbot.internals.utils.message.sendEmbed
import me.melijn.melijnbot.internals.utils.message.sendMsgAwaitEL
import me.melijn.melijnbot.internals.utils.message.sendRsp
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.*
import java.awt.Color
import java.time.ZoneId

class WarnCommand : AbstractCommand("command.warn") {

    init {
        id = 32
        name = "warn"
        commandCategory = CommandCategory.MODERATION
    }

    override suspend fun execute(context: ICommandContext) {
        if (argSizeCheckFailed(context, 0)) return

        val targetMember = retrieveMemberByArgsNMessage(context, 0, true, botAllowed = false) ?: return
        if (ModUtil.cantPunishAndReply(context, targetMember)) return

        var reason = context.rawArg
            .removeFirst(context.args[0])
            .trim()

        if (reason.isBlank()) {
            reason = "/"
        }

        val warn = Warn(
            context.guildId,
            targetMember.idLong,
            context.authorId,
            reason
        )

        val warning = context.getTranslation("message.warning")

        val privateChannel = targetMember.user.openPrivateChannel().awaitOrNull()
        val message: Message? = privateChannel?.let {
            sendMsgAwaitEL(it, warning)
        }?.firstOrNull()

        continueWarning(context, targetMember, warn, message)
    }

    private suspend fun continueWarning(
        context: ICommandContext,
        targetMember: Member,
        warn: Warn,
        warningMessage: Message? = null
    ) {
        val guild = context.guild
        val author = context.author
        val daoManager = context.daoManager
        val zoneId = getZoneId(daoManager, guild.idLong)
        val privZoneId = getZoneId(daoManager, guild.idLong, targetMember.idLong)
        val language = context.getLanguage()
        val warnedMessageDm = getWarnMessage(language, privZoneId, guild, targetMember.user, author, warn)
        val warnedMessageLc = getWarnMessage(
            language,
            zoneId,
            guild,
            targetMember.user,
            author,
            warn,
            true,
            targetMember.user.isBot,
            warningMessage != null
        )

        context.daoManager.warnWrapper.addWarn(warn)

        warningMessage?.editMessage(
            warnedMessageDm
        )?.override(true)?.queue()

        val logChannel = guild.getAndVerifyLogChannelByType(daoManager, LogChannelType.WARN)
        logChannel?.let { it1 ->
            sendEmbed(daoManager.embedDisabledWrapper, it1, warnedMessageLc)
        }

        val msg = context.getTranslation("$root.success")
            .withSafeVariable(PLACEHOLDER_USER, targetMember.asTag)
            .withSafeVarInCodeblock("reason", warn.reason)
        sendRsp(context, msg)
    }
}

fun getWarnMessage(
    language: String,
    zoneId: ZoneId,
    guild: Guild,
    warnedUser: User,
    warnAuthor: User,
    warn: Warn,
    lc: Boolean = false,
    isBot: Boolean = false,
    received: Boolean = true
): MessageEmbed {
    var description = "```LDIF\n"
    if (!lc) {
        description += i18n.getTranslation(language, "message.punishment.description.nlc")
            .withSafeVarInCodeblock("serverName", guild.name)
            .withVariable("serverId", guild.id)
    }

    description += i18n.getTranslation(language, "message.punishment.warn.description")
        .withSafeVarInCodeblock("warnAuthor", warnAuthor.asTag)
        .withVariable("warnAuthorId", warnAuthor.id)
        .withSafeVarInCodeblock("warned", warnedUser.asTag)
        .withVariable("warnedId", warnedUser.id)
        .withSafeVarInCodeblock("reason", warn.reason.take(1600))
        .withVariable("moment", (warn.moment.asEpochMillisToDateTime(zoneId)))
        .withVariable("warnId", warn.warnId)

    val extraDesc: String = if (!received || isBot) {
        i18n.getTranslation(
            language,
            if (isBot) {
                "message.punishment.extra.bot"
            } else {
                "message.punishment.extra.dm"
            }
        )
    } else {
        ""
    }
    description += extraDesc
    description += "```"

    val author = i18n.getTranslation(language, "message.punishment.warn.author")
        .withSafeVariable(PLACEHOLDER_USER, warnAuthor.asTag)
        .withVariable("spaces", getAtLeastNCodePointsAfterName(warnAuthor) + "\u200B")

    return EmbedBuilder()
        .setAuthor(author, null, warnAuthor.effectiveAvatarUrl)
        .setDescription(description)
        .setThumbnail(warnedUser.effectiveAvatarUrl)
        .setColor(Color.YELLOW)
        .build()
}