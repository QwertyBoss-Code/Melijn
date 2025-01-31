package me.melijn.melijnbot.internals.utils.message

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import me.melijn.melijnbot.Container
import me.melijn.melijnbot.commands.games.RockPaperScissorsGame
import me.melijn.melijnbot.database.DaoManager
import me.melijn.melijnbot.database.supporter.SupporterWrapper
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.command.PLACEHOLDER_PREFIX
import me.melijn.melijnbot.internals.models.EmbedEditor
import me.melijn.melijnbot.internals.models.ModularMessage
import me.melijn.melijnbot.internals.models.PodInfo
import me.melijn.melijnbot.internals.threading.TaskManager
import me.melijn.melijnbot.internals.translation.i18n
import me.melijn.melijnbot.internals.utils.*
import me.melijn.melijnbot.objectMapper
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.MessageBuilder.SplitPolicy
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.internal.entities.DataMessage
import net.dv8tion.jda.internal.entities.UserImpl

suspend fun sendSyntax(context: ICommandContext, translationPath: String = context.commandOrder.last().syntax) {
    val syntax = context.getTranslation("message.command.usage")
        .withVariable(
            "syntax", getSyntax(context, translationPath)
                .withVariable(PLACEHOLDER_PREFIX, context.usedPrefix)
        )
    sendRsp(context, syntax)
}

suspend fun getSyntax(context: ICommandContext, translationPath: String): String {
    return "%prefix%" + context.getTranslation(translationPath)
}

fun getSyntax(lang: String, translationPath: String): String {
    return "%prefix%" + i18n.getTranslation(lang, translationPath)
}

fun escapeForLog(string: String): String {
    return string
        .replace("`", "´")
        .replace("\n", " ")
        .trim()
}

// Returns successState
suspend fun sendOnShard0(
    context: ICommandContext,
    user: User,
    editor: EmbedEditor,
    extra: String
): Boolean {
    return try {
        if (PodInfo.podId == 0) {
            sendPrivateMessageExtra(user as UserImpl, editor, extra)
        } else {
            val hostPattern = context.container.settings.botInfo.hostPattern
            val url = hostPattern.replace("{podId}", 0) + "/senddm/${user.idLong}/$extra"
            val editorJson = objectMapper.writeValueAsString(editor)
            val res = objectMapper.readValue(
                context.webManager.httpClient.post<String>(url) {
                    body = editorJson
                }, Boolean::class.java
            )
            res
        }
    } catch (t: Throwable) {
        t.sendInGuild(context)
        t.printStackTrace()
        false
    }
}

suspend fun sendPrivateMessageExtra(
    user: UserImpl,
    embedEditor: EmbedEditor,
    extra: String
): Boolean {
    return user.openPrivateChannel().awaitOrNull()?.sendMessage(embedEditor.build())?.awaitOrNull()?.run {
        if (extra == "RPS") {
            this.addReaction(RockPaperScissorsGame.RPS.ROCK.unicode).queue() // rock 🪨
            this.addReaction(RockPaperScissorsGame.RPS.PAPER.unicode).queue() // paper 📰
            this.addReaction(RockPaperScissorsGame.RPS.SCISSORS.unicode).queue() // scissors ✂
        }
        true
    } ?: false
}

fun sendMsg(context: ICommandContext, msg: String) {
    if (context.isFromGuild) {
        sendMsg(context.textChannel, msg)
    } else {
        sendMsg(context.privateChannel, msg)
    }
}

suspend fun canResponse(messageChannel: MessageChannel, supporterWrapper: SupporterWrapper): Boolean {
    return if (messageChannel is TextChannel)
        supporterWrapper.getGuilds().contains(messageChannel.guild.idLong)
    else false
}

suspend fun sendRsp(context: ICommandContext, msg: String) {
    if (canResponse(context.channel, context.daoManager.supporterWrapper)) {
        sendRsp(context.textChannel, context.daoManager, msg)
    } else {
        sendMsg(context, msg)
    }
}

fun sendRsp(channel: TextChannel, daoManager: DaoManager, msg: String) {
    require(channel.canTalk()) { "Cannot talk in this channel " + channel.name }

    if (msg.length <= 2000) {
        channel.sendMessage(msg).async { message ->
            handleRspDelete(daoManager, message)
        }
    } else {
        val msgParts = StringUtils.splitMessage(msg)

        TaskManager.async(channel) {
            val msgList = mutableListOf<Message>()
            for (text in msgParts) {
                val oneMessage = channel.sendMessage(text).awaitOrNull() ?: continue
                msgList.add(oneMessage)
            }

            handleRspDelete(daoManager, msgList)
        }
    }
}

suspend fun sendRsp(textChannel: MessageChannel, context: ICommandContext, msg: ModularMessage) {
    if (textChannel is TextChannel && canResponse(textChannel, context.daoManager.supporterWrapper)) {
        sendRsp(textChannel, context.webManager.proxiedHttpClient, context.daoManager, msg)
    } else {
        sendMsg(textChannel, context.webManager.proxiedHttpClient, msg)
    }
}

suspend fun sendRspOrMsg(textChannel: TextChannel, daoManager: DaoManager, msg: String) {
    if (canResponse(textChannel, daoManager.supporterWrapper)) {
        sendRsp(textChannel, daoManager, msg)
    } else {
        sendMsg(textChannel, msg)
    }
}

suspend fun sendRsp(channel: TextChannel, httpClient: HttpClient, daoManager: DaoManager, msg: ModularMessage) {
    val message: Message? = msg.toMessage()
    when {
        message == null -> sendRspAttachments(daoManager, httpClient, channel, msg.attachments)
        msg.attachments.isNotEmpty() -> sendRspWithAttachments(
            daoManager,
            httpClient,
            channel,
            message,
            msg.attachments
        )
        else -> sendRsp(channel, daoManager, message)
    }
}

suspend fun sendRsp(channel: TextChannel, daoManager: DaoManager, message: Message) {
    require(channel.canTalk()) {
        "Cannot talk in this channel: #(${channel.name}, ${channel.id}) - ${channel.guild.id}"
    }

    if ((channel.guild.selfMember.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS) &&
            !daoManager.embedDisabledWrapper.embedDisabledCache.contains(channel.guild.idLong)) ||
        message.embeds.isEmpty()
    ) {
        val msg = try {
            channel.sendMessage(message).await()
        } catch (t: Throwable) {
            t.printStackTrace()
            return
        }

        handleRspDelete(daoManager, msg)
    } else {
        val mb = MessageBuilder(message)
            .setEmbeds(emptyList())

        val stringed = "\n" + message.embeds.joinToString("\n\n") { it.toMessage() }
        mb.append(stringed)
        val messages = mb.buildAll(
            SplitPolicy { i: Int, b: MessageBuilder -> (i + Message.MAX_CONTENT_LENGTH).coerceAtMost(b.length()) }
        )
        for (noEmbedMsg in messages) {
            sendRsp(channel, daoManager, noEmbedMsg)
        }
    }
}

suspend fun sendRsp(channel: PrivateChannel, daoManager: DaoManager, message: Message) {
    val msg = channel.sendMessage(message).awaitOrNull() ?: return

    handleRspDelete(daoManager, msg)
}

suspend fun sendMsgAwaitN(privateChannel: PrivateChannel, httpClient: HttpClient, msg: ModularMessage): Message? {
    val message: Message? = msg.toMessage()
    return when {
        message == null -> sendAttachmentsAwaitN(privateChannel, httpClient, msg.attachments)
        msg.attachments.isNotEmpty() -> sendMsgWithAttachmentsAwaitN(
            privateChannel,
            httpClient,
            message,
            msg.attachments
        )
        else -> sendMsgAwaitN(privateChannel, message)
    }
}

suspend fun sendRspAwaitN(
    textChannel: TextChannel,
    httpClient: HttpClient,
    daoManager: DaoManager,
    msg: ModularMessage
): Message? {
    val message: Message? = msg.toMessage()
    return when {
        message == null -> sendAttachmentsRspAwaitN(textChannel, httpClient, daoManager, msg.attachments)
        msg.attachments.isNotEmpty() -> sendRspWithAttachmentsAwaitN(
            textChannel,
            httpClient,
            daoManager,
            message,
            msg.attachments
        )
        else -> sendRspAwaitN(textChannel, daoManager, message)
    }
}

suspend fun sendMsgAwaitN(textChannel: TextChannel, httpClient: HttpClient, msg: ModularMessage): Message? {
    val message: Message? = msg.toMessage()
    return when {
        message == null -> sendAttachmentsAwaitN(textChannel, httpClient, msg.attachments)
        msg.attachments.isNotEmpty() -> sendMsgWithAttachmentsAwaitN(textChannel, httpClient, message, msg.attachments)
        else -> sendMsgAwaitN(textChannel, message)
    }
}

suspend fun sendMsg(textChannel: MessageChannel, httpClient: HttpClient, msg: ModularMessage) {
    val message: Message? = msg.toMessage()
    when {
        message == null -> sendAttachments(textChannel, httpClient, msg.attachments)
        msg.attachments.isNotEmpty() -> sendMsgWithAttachments(textChannel, httpClient, message, msg.attachments)
        else -> sendMsg(textChannel, message)
    }
}

suspend fun sendMsgAwaitEL(privateChannel: PrivateChannel, msg: String): List<Message> {
    val messageList = mutableListOf<Message>()
    if (privateChannel.user.isBot) return emptyList()
    if (msg.length <= 2000) {
        privateChannel.sendMessage(msg).awaitOrNull()?.let { messageList.add(it) }
    } else {
        val msgParts = StringUtils.splitMessage(msg).withIndex()
        for ((index, text) in msgParts) {
            privateChannel.sendMessage(text).awaitOrNull()?.let { messageList.add(index, it) }
        }
    }

    return messageList
}

fun sendMsg(privateChannel: PrivateChannel, msg: String) {
    if (privateChannel.user.isBot) return
    if (msg.length <= 2000) {

        privateChannel.sendMessage(msg).queue()
    } else {
        val msgParts = StringUtils.splitMessage(msg)

        for (text in msgParts) {
            privateChannel.sendMessage(text).queue()
        }
    }
}

suspend fun sendRspAwaitEL(context: ICommandContext, msg: String): List<Message> {
    return if (canResponse(context.channel, context.daoManager.supporterWrapper)) {
        sendRspAwaitEL(context.textChannel, context.daoManager, msg)
    } else {
        sendMsgAwaitEL(context, msg)
    }
}

suspend fun sendRspAwaitEL(channel: TextChannel, daoManager: DaoManager, msg: String): List<Message> {
    require(channel.canTalk()) { "Cannot talk in this channel " + channel.name }

    val messageList = mutableListOf<Message>()
    if (msg.length <= 2000) {
        val message = channel.sendMessage(msg).awaitOrNull() ?: return messageList
        messageList.add(message)

        TaskManager.async(channel) {
            handleRspDelete(daoManager, message)
        }

    } else {
        val msgParts = StringUtils.splitMessage(msg).withIndex()
        for ((index, text) in msgParts) {
            val message = channel.sendMessage(text).awaitOrNull() ?: continue
            messageList.add(index, message)

            TaskManager.async(channel) {
                handleRspDelete(daoManager, message)
            }
        }
    }

    return messageList
}

suspend fun sendMsgAwaitEL(context: ICommandContext, msg: String): List<Message> {
    return if (context.isFromGuild) {
        sendMsgAwaitEL(context.textChannel, msg)
    } else {
        sendMsgAwaitEL(context.privateChannel, msg)
    }
}

suspend fun sendMsgAwaitEL(channel: TextChannel, msg: String): List<Message> {
    require(channel.canTalk()) { "Cannot talk in this channel " + channel.name }

    val messageList = mutableListOf<Message>()
    if (msg.length <= 2000) {

        channel.sendMessage(msg).awaitOrNull()?.let { messageList.add(it) }
    } else {
        val msgParts = StringUtils.splitMessage(msg).withIndex()
        for ((index, text) in msgParts) {

            channel.sendMessage(text).awaitOrNull()?.let { messageList.add(index, it) }
        }
    }

    return messageList
}

fun sendMsg(channel: TextChannel, msg: String) {
    require(channel.canTalk()) { "Cannot talk in this channel #" + channel.name }

    if (msg.length <= 2000) {
        channel.sendMessage(msg).queue()
    } else {
        val msgParts = StringUtils.splitMessage(msg)

        for (text in msgParts) {
            channel.sendMessage(text).queue()
        }
    }
}

suspend fun sendMsg(
    channel: TextChannel,
    msg: String,
    success: ((messages: List<Message>) -> Unit)? = null,
    failed: ((ex: Throwable) -> Unit)? = null
) {
    require(channel.canTalk()) {
        "Cannot talk in this channel: #(${channel.name}, ${channel.id}) - ${channel.guild.id}"
    }
    try {
        val messageList = mutableListOf<Message>()
        if (msg.length <= 2000) {
            channel.sendMessage(msg).awaitOrNull()?.let { messageList.add(it) }
        } else {
            val msgParts = StringUtils.splitMessage(msg).withIndex()
            for ((index, text) in msgParts) {
                channel.sendMessage(text).awaitOrNull()?.let { messageList.add(index, it) }
            }

        }
        success?.invoke(messageList)
    } catch (t: Throwable) {
        t.printStackTrace()
        failed?.invoke(t)
        return
    }
}

fun sendMsg(
    channel: MessageChannel,
    msg: Message,
    success: ((messages: Message) -> Unit)? = null,
    failed: ((ex: Throwable) -> Unit)? = null
) {
    if (channel is TextChannel) {
        require(channel.canTalk()) {
            "Cannot talk in this channel: #(${channel.name}, ${channel.id}) - ${channel.guild.id}"
        }
    }

    val mb = MessageBuilder()
    if (msg.contentRaw.isNotBlank()) mb.setContent(msg.contentRaw)

    for (embed in msg.embeds) {
        mb.setEmbeds(embed)
    }
    if (msg is DataMessage) {
        mb.setAllowedMentions(msg.allowedMentions)
    }

    channel.sendMessage(mb.build()).queue(success, failed)
}

suspend fun sendMsgAwaitN(channel: TextChannel, msg: Message): Message? {
    require(channel.canTalk()) {
        "Cannot talk in this channel: #(${channel.name}, ${channel.id}) - ${channel.guild.id}"
    }

    val action = channel.sendMessage(msg)
    if (msg is DataMessage)
        action.allowedMentions(msg.allowedMentions)

    return action.awaitOrNull()
}

suspend fun sendMsgAwaitN(channel: PrivateChannel, msg: Message): Message? {
    if (channel.user.isBot) return null

    val action = channel.sendMessage(msg)
    if (msg is DataMessage)
        action.allowedMentions(msg.allowedMentions)

    return action.awaitOrNull()
}

suspend fun sendFeatureRequiresPremiumMessage(
    context: ICommandContext,
    featurePath: String,
    featureReplaceMap: Map<String, String> = emptyMap()
) {
    var feature = context.getTranslation(featurePath)
    for ((key, replacement) in featureReplaceMap) {
        feature = feature.replace("%$key%", replacement)
    }

    val baseMsg = context.getTranslation("message.feature.requires.premium")
        .withVariable("feature", feature)
        .withVariable("prefix", context.usedPrefix)
    sendRsp(context, baseMsg)
}

suspend fun sendFeatureRequiresGuildPremiumMessage(
    context: ICommandContext,
    featurePath: String,
    featureReplaceMap: Map<String, String> = emptyMap()
) {
    var feature = context.getTranslation(featurePath)
    for ((key, replacement) in featureReplaceMap) {
        feature = feature.replace("%$key%", replacement)
    }

    val baseMsg = context.getTranslation("message.feature.requires.premium.server")
        .withVariable("feature", feature)
        .withVariable("prefix", context.usedPrefix)
    sendRsp(context, baseMsg)
}

fun getNicerUsedPrefix(selfUser: User, prefix: String): String {
    return if (prefix.contains(selfUser.id) && USER_MENTION.matches(prefix)) {
        "@${selfUser.name} "
    } else {
        prefix
    }
}

suspend fun handleRspDelete(daoManager: DaoManager, msgList: MutableList<Message>) {
    val channel = msgList.first().textChannel
    val timeMap = daoManager.removeResponseWrapper.getMap(channel.guild.idLong)
    val seconds = timeMap[channel.idLong] ?: return
    delay(seconds * 1000L)

    val msgIds = msgList.map { it1 -> it1.idLong }
    Container.instance.botDeletedMessageIds.addAll(msgIds)

    channel.deleteMessagesByIds(
        msgIds.map { it.toString() }
    ).queue(null) { Container.instance.botDeletedMessageIds.removeAll(msgIds) }

}

suspend fun handleRspDelete(daoManager: DaoManager, message: Message) {
    if (message.channel !is TextChannel) return
    val timeMap = daoManager.removeResponseWrapper.getMap(message.textChannel.guild.idLong)
    val seconds = timeMap[message.textChannel.idLong] ?: timeMap[message.guild.idLong] ?: return

    delay(seconds * 1000L)
    Container.instance.botDeletedMessageIds.add(message.idLong)

    message.delete().queue(null) { Container.instance.botDeletedMessageIds.remove(message.idLong) }
}