package me.melijn.melijnbot.internals.music

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.wrapper.spotify.model_objects.specification.ArtistSimplified
import com.wrapper.spotify.model_objects.specification.Track
import com.wrapper.spotify.model_objects.specification.TrackSimplified
import me.melijn.melijnbot.commands.music.NextSongPosition
import me.melijn.melijnbot.database.DaoManager
import me.melijn.melijnbot.database.audio.SongCacheWrapper
import me.melijn.melijnbot.enums.SearchType
import me.melijn.melijnbot.internals.command.CommandContext
import me.melijn.melijnbot.internals.embed.Embedder
import me.melijn.melijnbot.internals.threading.TaskManager
import me.melijn.melijnbot.internals.translation.PLACEHOLDER_USER
import me.melijn.melijnbot.internals.translation.SC_SELECTOR
import me.melijn.melijnbot.internals.translation.YT_SELECTOR
import me.melijn.melijnbot.internals.utils.*
import me.melijn.melijnbot.internals.utils.message.sendEmbedRsp
import me.melijn.melijnbot.internals.utils.message.sendEmbedRspAwaitEL
import me.melijn.melijnbot.internals.utils.message.sendRsp
import me.melijn.melijnbot.internals.utils.message.sendRspAwaitEL
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.VoiceChannel
import java.lang.Integer.min

const val QUEUE_LIMIT = 150
const val DONATE_QUEUE_LIMIT = 1000

class AudioLoader(private val musicPlayerManager: MusicPlayerManager) {

    val root = "message.music"
    private val audioPlayerManager = musicPlayerManager.audioPlayerManager
    private val ytSearch = YTSearch()
    private val spotifyTrackDiff = 10_000


    suspend fun foundSingleTrack(
        context: CommandContext,
        guildMusicPlayer: GuildMusicPlayer,
        wrapper: SongCacheWrapper,
        track: AudioTrack,
        rawInput: String,
        nextPos: NextSongPosition
    ) {
        track.userData = TrackUserData(context.author)
        if (guildMusicPlayer.safeQueue(context, track, nextPos)) {
            sendMessageAddedTrack(context, track)

            LogUtils.addMusicPlayerNewTrack(context, track)
            wrapper.addTrack(rawInput, track) // add new track hit
        }
    }

    suspend fun foundTracks(
        context: CommandContext,
        guildMusicPlayer: GuildMusicPlayer,
        wrapper: SongCacheWrapper,
        tracks: List<AudioTrack>,
        rawInput: String,
        isPlaylist: Boolean,
        nextPos: NextSongPosition
    ) {
        if (isPlaylist) {
            var notAdded = 0

            for (track in tracks) {
                track.userData = TrackUserData(context.author)

                if (!guildMusicPlayer.safeQueueSilent(context.daoManager, track, nextPos)) notAdded++
                else {
                    LogUtils.addMusicPlayerNewTrack(context, track)
                }
            }
            sendMessageAddedTracks(context, tracks.subList(0, tracks.size - notAdded))
        } else {
            foundSingleTrack(context, guildMusicPlayer, wrapper, tracks[0], rawInput, nextPos)
        }
    }

    suspend fun loadNewTrackNMessage(context: CommandContext, source: String, isPlaylist: Boolean = false, nextPos: NextSongPosition) {
        val guild = context.guild
        val guildMusicPlayer = musicPlayerManager.getGuildMusicPlayer(guild)
        val searchType = when {
            source.startsWith(YT_SELECTOR) -> SearchType.YT
            source.startsWith(SC_SELECTOR) -> SearchType.SC
            else -> SearchType.LINK
        }

        val rawInput = source
            .removePrefix(YT_SELECTOR)
            .removePrefix(SC_SELECTOR)

        if (guildMusicPlayer.queueIsFull(context, 1)) return
        val wrapper = context.daoManager.songCacheWrapper
        val resultHandler = object : SuspendingAudioLoadResultHandler {
            override suspend fun loadFailed(exception: FriendlyException) {
                sendMessageLoadFailed(context, exception)
            }

            override suspend fun trackLoaded(track: AudioTrack) {
                foundSingleTrack(context, guildMusicPlayer, wrapper, track, rawInput, nextPos)
            }

            override suspend fun noMatches() {
                sendMessageNoMatches(context, rawInput)
            }

            override suspend fun playlistLoaded(playlist: AudioPlaylist) {
                foundTracks(context, guildMusicPlayer, wrapper, playlist.tracks, rawInput, isPlaylist, nextPos)
            }
        }

        val audioTrack = wrapper.getTrackInfo(rawInput)
        if (audioTrack != null && !source.startsWith(SC_SELECTOR)) { // track found and made from cache
            foundSingleTrack(context, guildMusicPlayer, wrapper, audioTrack, rawInput, nextPos)
            return
        }

        try {
            ytSearch.search(rawInput, searchType, { tracks ->
                if (tracks.isNotEmpty()) {
                    foundTracks(context, guildMusicPlayer, wrapper, tracks, rawInput, isPlaylist, nextPos)
                } else {
                    sendMessageNoMatches(context, rawInput)
                }
            }, { //LLDisabledAndNotYTSearch
                audioPlayerManager.loadItemOrdered(guildMusicPlayer, source, resultHandler)
            }, resultHandler)
        } catch (t: Throwable) {
            sendMessageLoadFailed(context, t)
        }
    }


    private suspend fun sendMessageLoadFailed(context: CommandContext, exception: Throwable) {
        val msg = context.getTranslation("$root.loadfailed")
            .withVariable("cause", exception.message ?: "/")
        sendRsp(context, msg)
        exception.printStackTrace()
    }

    suspend fun sendMessageNoMatches(context: CommandContext, input: String) {
        val msg = context.getTranslation("$root.nomatches")
            .withVariable("source", input)
        sendRsp(context, msg)
    }


    suspend fun sendMessageAddedTrack(context: CommandContext, audioTrack: AudioTrack) {
        val title = context.getTranslation("$root.addedtrack.title")
            .withVariable(PLACEHOLDER_USER, context.author.asTag)
        val description = context.getTranslation("$root.addedtrack.description")
            .withVariable("position", getQueuePosition(context, audioTrack).toString())
            .withVariable("title", audioTrack.info.title)
            .withVariable("duration", getDurationString(audioTrack.duration))
            .withVariable("url", audioTrack.info.uri)

        val eb = Embedder(context)
            .setTitle(title)
            .setDescription(description)

        sendEmbedRsp(context, eb.build())
    }

    private suspend fun sendMessageAddedTracks(context: CommandContext, audioTracks: List<AudioTrack>) {
        val title = context.getTranslation("$root.addedtracks.title")
            .withVariable(PLACEHOLDER_USER, context.author.asTag)
        val description = context.getTranslation("$root.addedtracks.description")
            .withVariable("size", audioTracks.size.toString())
            .withVariable("positionFirst", getQueuePosition(context, audioTracks[0]).toString())
            .withVariable("positionLast", getQueuePosition(context, audioTracks[audioTracks.size - 1]).toString())

        val eb = Embedder(context)
            .setTitle(title)
            .setDescription(description)

        sendEmbedRsp(context, eb.build())
    }

    private fun getQueuePosition(context: CommandContext, audioTrack: AudioTrack): Int =
        context.musicPlayerManager.getGuildMusicPlayer(context.guild).guildTrackManager.getPosition(audioTrack)

    suspend fun loadSpotifyTrack(
        context: CommandContext,
        query: String,
        artists: Array<ArtistSimplified>?,
        durationMs: Int,
        silent: Boolean = false,
        nextPos: NextSongPosition,
        loaded: (suspend (Boolean) -> Unit)? = null
    ) {
        val player: GuildMusicPlayer = context.getGuildMusicPlayer()
        val title: String = query
            .removeFirst(SC_SELECTOR)
            .removeFirst(YT_SELECTOR)
        val source = StringBuilder(query)
        val artistNames = mutableListOf<String>()
        if (player.queueIsFull(context, 1, silent)) {
            loaded?.invoke(false)
            return
        }
        appendArtists(artists, source, artistNames)

        val fullQuery = source.toString()
        var justQuery = fullQuery.removePrefix(SC_SELECTOR)
        val type = if (justQuery == fullQuery) {
            justQuery = fullQuery.removePrefix(YT_SELECTOR)
            SearchType.YT
        } else {
            SearchType.SC
        }

        val resultHandler = object : SuspendingAudioLoadResultHandler {
            override suspend fun trackLoaded(track: AudioTrack) {
                if ((durationMs + spotifyTrackDiff > track.duration && track.duration > durationMs - spotifyTrackDiff)
                    || track.info.title.contains(title, true)) {
                    track.userData = TrackUserData(context.author)
                    if (player.safeQueue(context, track, nextPos)) {
                        if (!silent) {
                            sendMessageAddedTrack(context, track)
                        }

                        LogUtils.addMusicPlayerNewTrack(context, track)

                        loaded?.invoke(true)
                    } else {
                        loaded?.invoke(false)
                    }
                } else {
                    loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, nextPos, loaded)
                }
            }

            override suspend fun playlistLoaded(playlist: AudioPlaylist) {
                val tracks: List<AudioTrack> = playlist.tracks
                for (track in tracks.subList(0, min(tracks.size, 5))) {
                    if ((durationMs + spotifyTrackDiff > track.duration && track.duration > durationMs - spotifyTrackDiff)
                        || track.info.title.contains(title, true)) {
                        track.userData = TrackUserData(context.author)
                        if (player.safeQueue(context, track, nextPos)) {
                            if (!silent) {
                                sendMessageAddedTrack(context, track)
                            }

                            LogUtils.addMusicPlayerNewTrack(context, track)

                            loaded?.invoke(true)
                        } else {
                            loaded?.invoke(false)
                        }
                        return
                    }
                }
                loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, nextPos, loaded)
            }

            override suspend fun noMatches() {
                loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, nextPos, loaded)
            }

            override suspend fun loadFailed(exception: FriendlyException) {
                if (!silent) {
                    sendMessageLoadFailed(context, exception)
                }
                loaded?.invoke(false)
            }
        }

        ytSearch.search(justQuery, type, { tracks ->
            if (tracks.isEmpty()) {
                loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, nextPos, loaded)
                return@search
            }
            for (track in tracks.subList(0, min(tracks.size, 5))) {
                if ((durationMs + spotifyTrackDiff > track.duration && track.duration > durationMs - spotifyTrackDiff)
                    || track.info.title.contains(title, true)) {
                    track.userData = TrackUserData(context.author)
                    if (player.safeQueue(context, track, nextPos)) {
                        if (!silent) {
                            sendMessageAddedTrack(context, track)
                        }

                        LogUtils.addMusicPlayerNewTrack(context, track)

                        loaded?.invoke(true)
                    } else {
                        loaded?.invoke(false)
                    }
                    return@search
                }
            }
            loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, nextPos, loaded)

        }, {
            //LLDisabledAndNotYTSearch
            audioPlayerManager.loadItemOrdered(player, query, resultHandler)
        }, resultHandler)
    }

    private suspend fun loadSpotifyTrackOther(
        context: CommandContext,
        query: String,
        artists: Array<ArtistSimplified>?,
        durationMs: Int,
        title: String,
        silent: Boolean = false,
        nextPos: NextSongPosition,
        loaded: (suspend (Boolean) -> Unit)? = null
    ) {
        if (query.startsWith(YT_SELECTOR)) {
            if (artists != null) {
                loadSpotifyTrack(context, query, null, durationMs, silent, nextPos, loaded)
            } else {
                val newQuery = query.replaceFirst(YT_SELECTOR, SC_SELECTOR)
                loadSpotifyTrack(context, newQuery, artists, durationMs, silent, nextPos, loaded)
            }
        } else if (query.startsWith(SC_SELECTOR)) {
            if (artists != null) {
                loadSpotifyTrack(context, query, null, durationMs, silent, nextPos, loaded)
            } else {
                if (!silent) sendMessageNoMatches(context, title)
                loaded?.invoke(false)
            }
        }
    }

    private fun appendArtists(artists: Array<ArtistSimplified>?, source: StringBuilder, artistNames: MutableList<String>) {
        if (artists != null) {
            if (artists.isNotEmpty()) source.append(" ")
            val artistString = artists.joinToString(", ", transform = { artist ->
                artistNames.add(artist.name)
                artist.name
            })
            source.append(artistString)
        }
    }

    suspend fun loadSpotifyPlaylist(context: CommandContext, tracks: Array<Track>, nextPos: NextSongPosition) {
        if (tracks.size + context.getGuildMusicPlayer().guildTrackManager.tracks.size > QUEUE_LIMIT) {
            val msg = context.getTranslation("$root.queuelimit")
                .withVariable("amount", QUEUE_LIMIT.toString())

            sendRsp(context, msg)
            return
        }

        val loadedTracks = mutableListOf<Track>()
        val failedTracks = mutableListOf<Track>()
        val msg = context.getTranslation("command.play.loadingtrack" + if (tracks.size > 1) "s" else "")
            .withVariable("trackCount", tracks.size.toString())
            .withVariable("donateAmount", DONATE_QUEUE_LIMIT.toString())

        val message = sendRspAwaitEL(context, msg)
        for (track in tracks) {
            loadSpotifyTrack(context, YT_SELECTOR + track.name, track.artists, track.durationMs, true, nextPos) {
                if (it) {
                    loadedTracks.add(track)
                } else {
                    failedTracks.add(track)
                }
                if (loadedTracks.size + failedTracks.size == tracks.size) {
                    val newMsg = context.getTranslation("command.play.loadedtrack" + if (tracks.size > 1) "s" else "")
                        .withVariable("loadedCount", loadedTracks.size.toString())
                        .withVariable("failedCount", failedTracks.size.toString())
                    message[0].editMessage(newMsg).await()
                }
            }
        }
    }

    suspend fun loadSpotifyAlbum(context: CommandContext, simpleTracks: Array<TrackSimplified>, nextPos: NextSongPosition) {
        if (simpleTracks.size + context.getGuildMusicPlayer().guildTrackManager.tracks.size > QUEUE_LIMIT) {
            val msg = context.getTranslation("$root.queuelimit")
                .withVariable("amount", QUEUE_LIMIT.toString())
                .withVariable("donateAmount", DONATE_QUEUE_LIMIT.toString())
            sendRsp(context, msg)
            return
        }

        val loadedTracks = mutableListOf<TrackSimplified>()
        val failedTracks = mutableListOf<TrackSimplified>()
        val msg = context.getTranslation("command.play.loadingtrack" + if (simpleTracks.size > 1) "s" else "")
            .withVariable("trackCount", simpleTracks.size.toString())
        val message = sendRspAwaitEL(context, msg)
        for (track in simpleTracks) {
            loadSpotifyTrack(context, YT_SELECTOR + track.name, track.artists, track.durationMs, true, nextPos) {
                if (it) {
                    loadedTracks.add(track)
                } else {
                    failedTracks.add(track)
                }
                if (loadedTracks.size + failedTracks.size == simpleTracks.size) {
                    val newMsg = context.getTranslation("command.play.loadedtrack" + if (simpleTracks.size > 1) "s" else "")
                        .withVariable("loadedCount", loadedTracks.size.toString())
                        .withVariable("failedCount", failedTracks.size.toString())
                    message[0].editMessage(newMsg).await()
                }
            }
        }
    }

    suspend fun loadNewTrackPickerNMessage(context: CommandContext, query: String, nextPos: NextSongPosition) {
        val guildMusicPlayer = context.getGuildMusicPlayer()
        val rawInput = query
            .replace(YT_SELECTOR, "")
            .replace(SC_SELECTOR, "")

        if (guildMusicPlayer.queueIsFull(context, 1)) return
        val resultHandler = object : SuspendingAudioLoadResultHandler {
            override suspend fun loadFailed(exception: FriendlyException) {
                sendMessageLoadFailed(context, exception)
            }

            override suspend fun trackLoaded(track: AudioTrack) {
                prepareSearchMenu(context, listOf(track), nextPos)
            }

            override suspend fun noMatches() {
                sendMessageNoMatches(context, rawInput)
            }

            override suspend fun playlistLoaded(playlist: AudioPlaylist) {
                prepareSearchMenu(context, playlist.tracks, nextPos)
            }
        }

        audioPlayerManager.loadItemOrdered(guildMusicPlayer, query, resultHandler)
    }

    private suspend fun prepareSearchMenu(context: CommandContext, trackList: List<AudioTrack>, nextPos: NextSongPosition) {
        val guildMusicPlayer = context.getGuildMusicPlayer()
        if (guildMusicPlayer.queueIsFull(context, 1)) return

        val tracks = trackList.filterIndexed { index, _ -> index < 5 }.toMutableList()

        for ((index, track) in tracks.withIndex()) {
            track.userData = TrackUserData(context.author)
            tracks[index] = track
        }

        TaskManager.async(context) {
            val msg = sendMessageSearchMenu(context, tracks).last()
            guildMusicPlayer.searchMenus[msg.idLong] = TracksForQueue(tracks, nextPos)

            if (tracks.size > 0) msg.addReaction("\u0031\u20E3").queue()
            if (tracks.size > 1) msg.addReaction("\u0032\u20E3").queue()
            if (tracks.size > 2) msg.addReaction("\u0033\u20E3").queue()
            if (tracks.size > 3) msg.addReaction("\u0034\u20E3").queue()
            if (tracks.size > 4) msg.addReaction("\u0035\u20E3").queue()
            if (tracks.size > 5) msg.addReaction("\u0036\u20E3").queue()
            msg.addReaction("\u274C").queue()
        }
    }

    private suspend fun sendMessageSearchMenu(context: CommandContext, tracks: List<AudioTrack>): List<Message> {
        val title = context.getTranslation("$root.searchmenu")
        var menu = ""
        for ((index, track) in tracks.withIndex()) {
            menu += "\n[${index + 1}](${track.info.uri}) - ${track.info.title} `[${getDurationString(track.duration)}]`"
        }

        val eb = Embedder(context)
            .setTitle(title)
            .setDescription(menu)
        return sendEmbedRspAwaitEL(context, eb.build())
    }

    suspend fun loadNewTrack(daoManager: DaoManager, lavaManager: LavaManager, vc: VoiceChannel, author: User, source: String, nextPos: NextSongPosition) {
        val guild = vc.guild
        val guildMusicPlayer = musicPlayerManager.getGuildMusicPlayer(guild)

        val resultHandler = object : SuspendingAudioLoadResultHandler {
            override suspend fun loadFailed(exception: FriendlyException) {
                LogUtils.sendFailedLoadStreamTrackLog(daoManager, guild, source, exception)
            }

            override suspend fun trackLoaded(track: AudioTrack) {
                track.userData = TrackUserData(guild.selfMember.user)
                guildMusicPlayer.guildTrackManager.queue(track, nextPos)

                LogUtils.addMusicPlayerNewTrack(daoManager, lavaManager, vc, author, track)

            }

            override suspend fun noMatches() {
                return
            }

            override suspend fun playlistLoaded(playlist: AudioPlaylist) {
                return
            }
        }

        audioPlayerManager.loadItemOrdered(guildMusicPlayer, source, resultHandler)
    }
}

interface SuspendingAudioLoadResultHandler {
    /**
     * Called when the requested item is a track and it was successfully loaded.
     * @param track The loaded track
     */
    suspend fun trackLoaded(track: AudioTrack)

    /**
     * Called when the requested item is a playlist and it was successfully loaded.
     * @param playlist The loaded playlist
     */
    suspend fun playlistLoaded(playlist: AudioPlaylist)

    /**
     * Called when there were no items found by the specified identifier.
     */
    suspend fun noMatches()

    /**
     * Called when loading an item failed with an exception.
     * @param exception The exception that was thrown
     */
    suspend fun loadFailed(exception: FriendlyException)
}
