@file:Suppress("RemoveRedundantQualifierName")

package me.melijn.melijnbot.internals.command

import me.melijn.melijnbot.Container
import me.melijn.melijnbot.commands.administration.*
import me.melijn.melijnbot.commands.animal.*
import me.melijn.melijnbot.commands.anime.*
import me.melijn.melijnbot.commands.developer.*
import me.melijn.melijnbot.commands.economy.*
import me.melijn.melijnbot.commands.games.PokerCommand
import me.melijn.melijnbot.commands.games.RockPaperScissorsCommand
import me.melijn.melijnbot.commands.games.SlotsCommand
import me.melijn.melijnbot.commands.games.TicTacToeCommand
import me.melijn.melijnbot.commands.image.*
import me.melijn.melijnbot.commands.moderation.*
import me.melijn.melijnbot.commands.music.*
import me.melijn.melijnbot.commands.nsfw.*
import me.melijn.melijnbot.commands.utility.*
import me.melijn.melijnbot.internals.threading.TaskManager
import org.slf4j.LoggerFactory

class CommandClientBuilder(private val container: Container) {

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    init {
        logger.info("Loading commands...")
    }

    private val commands = hashSetOf(
        PunishmentCommand(),
        SetCommandStateCommand(),
        PunchCommand(),
        ColorCommand(),
        LoopCommand(),
        ShuffleCommand(),
        KickCommand(),
        SetLogChannelCommand(),
        KissCommand(),
        MirrorCommand(),
        LickCommand(),
        NyancatCommand(),
        SPlayCommand(),
        ForceRoleCommand(),
        TestCommand(),
        BiteCommand(),
        SetChannelCommand(),
        VolumeCommand(),
        GreyScaleCommand(),
        InfoCommand(),
        GifInfoCommand(),
        FilterCommand(),
        TempBanCommand(),
        DabCommand(),
        LoopQueueCommand(),
        HelpCommand(),
        PrefixesCommand(),
        MuteCommand(),
        StatsCommand(),
        CatCommand(),
        KoalaCommand(),
        ResumeCommand(),
        SoftBanCommand(),
        TrackInfoCommand(),
        RawCommand(),
        BirdCommand(),
        AvatarCommand(),
        SetCooldownCommand(),
        MetricsCommand(),
        UnmuteCommand(),
        PurgeCommand(),
        SeekCommand(),
        VoteCommand(),
        SetVerificationTypeCommand(),
        PlayCommand(),
        SetMusicChannelCommand(),
        FilterGroupCommand(),
        BlushCommand(),
        StareCommand(),
        TempMuteCommand(),
        SummonCommand(),
        VerifyCommand(),
        RewindCommand(),
        SetSlowModeCommand(),
        GreetCommand(),
        BanCommand(),
        InvertCommand(),
//        RestartCommand(),
        UrbanCommand(),
        HistoryCommand(),
        RoleInfoCommand(),
        AwooCommand(),
        AlpacaCommand(),
        PermissionCommand(),
        WarnCommand(),
        T2eCommand(),
        FlipImgCommand(),
        ClearChannelCommand(),
        ServerInfoCommand(),
        PatCommand(),
        SelfRoleCommand(),
        PingCommand(),
        HighfiveCommand(),
        InviteCommand(),
        BlurCommand(),
        SetEmbedColorCommand(),
        PandaCommand(),
        CuddleCommand(),
        SlapCommand(),
        EmoteCommand(),
        ForwardCommand(),
        OldBlurpleCommand(),
        SetMaxUserVerificationFlowRateCommand(),
        UnicodeCommand(),
        UnbanCommand(),
        SpookifyCommand(),
        HugCommand(),
        SetPrivateEmbedColorCommand(),
        StopCommand(),
        RolesCommand(),
        VoteInfoCommand(),
        FoxCommand(),
        PixelateCommand(),
        TickleCommand(),
        SetStreamUrlCommand(),
        SharpenCommand(),
        ThinkingCommand(),
        HandholdingCommand(),
        ShardsCommand(),
        SayCommand(),
        ShootCommand(),
        SettingsCommand(),
        RemoveCommand(),
        PokeCommand(),
        PauseCommand(),
        QueueCommand(),
        PrivatePrefixesCommand(),
        MeguminCommand(),
        NowPlayingCommand(),
        SetBandCommand(),
        SetEmbedStateCommand(),
        LewdCommand(),
        ShrugCommand(),
        PunishmentGroupCommand(),
        PoutCommand(),
        EvalCommand(),
        OwOCommand(),
        SmugCommand(),
        SetVerificationPasswordCommand(),
        DonateCommand(),
        CryCommand(),
        DiscordMemeCommand(),
        SetRoleCommand(),
        UserInfoCommand(),
        SetLanguageCommand(),
        SkipCommand(),
        SetVerificationEmotejiCommand(),
        DogCommand(),
        CustomCommandCommand(),
        ThumbsupCommand(),
        NekoCommand(),
        SetPrivateLanguageCommand(),
        SetBirthdayCommand(),
        SetPrivateTimeZoneCommand(),
        SetTimeZoneCommand(),
        SpamCommand(),
        GainProfileCommand(),
        DuckCommand(),
        AIWaifuCommand(),
        SetMusic247Command(),
        SupportCommand(),
        VoteSkipCommand(),
        RerenderGifCommand(),
        LyricsCommand(),
        AppendReverseGifCommand(),
        GlobalRecolorCommand(),
        MoveCommand(),
        JoinRoleCommand(),
        MassMoveCommand(),
        LimitRoleToChannelCommand(),
        SetBannedOrKickedTriggersLeaveCommand(),
        MyAnimeListCommand(container.settings.api.jikan),
        SetBotLogStateCommand(),
        SetRoleColorCommand(),
        AniListCommand(),
        NekoHCommand(),
        Rule34Command(),
        SafebooruCommand(),
        TBibCommand(),
        GelbooruCommand(),
        AngryCommand(),
        PngsFromGifCommand(),
        PngsToGifCommand(),
        ReplaceColorCommand(),
        SetAllowSpacedPrefixState(),
        SetPrivateAllowSpacedPrefixState(),
        AliasesCommand(),
        PrivateAliasesCommand(),
        ManageHistoryCommand(),
//        ShutdownCommand(),
        PenguinCommand(),
        SpeedCommand(),
        PitchCommand(),
        RateCommand(),
        PossumCommand(),
        BalanceCommand(),
        SetBalanceCommand(),
        SetRemoveResponsesCommand(),
        SetRemoveInvokeCommand(),
        ManageSupportersCommand(),
        me.melijn.melijnbot.commands.economy.FlipCommand(),
        DailyCommand(),
        PayCommand(),
        BassBoostCommand(),
        NightcoreCommand(),
        LeaderBoardCommand(),
        TopVotersCommand(),
        ToggleVoteReminderCommand(),
        MikuCommand(),
        RedisCommand(),
        RedditCommand(),
        MemeCommand(),
        BonkCommand(),
        OsuCommand(),
        EmotesCommand(),
        IDInfoCommand(),
        IAmCommand(),
        IAmNotCommand(),
        TokenInfoCommand(),
        ChannelInfoCommand(),
        ReverseImageSearchCommand(),
        GoogleReverseImageSearch(),
        BoostersCommand(),
        ClearCacheCommand(),
        SlotsCommand(),
        PokerCommand(),
        JailCommand(),
        // CalculateCommand(),
        SnipeCommand(),
        SnekCommand(),
        PlaylistCommand(),
        StarboardCommand(),
        LikeCommand(),
        RemindmeCommand(),
        ShipCommand(),
        MusicNodeCommand(),
        ClearQueueCommand(),
        TicTacToeCommand(),
        RockPaperScissorsCommand(),
        RepCommand(),
        BegCommand(),
        ChickenCommand(),
        FishCommand(),
        LockCommand(),
        UnlockCommand(),
        FlipXCommand(),
        TimeCommand(),
        NomCommand(),
        ConfusedCommand(),
        FrogCommand(),
        ChangeNameCommand(),
        ScriptsCommand(),
        TwitterCommand(),
        LynxCommand(),
        SyncChannelCommand(),
        MoveChannelCommand(),
        SetChannelCategory(),
        SetAutoRemoveInactiveJoinMessagesDuration(),
        MessageCommand(),
        LinkMessageCommand(),
        ChannelRoleCommand(),
        GiveRoleCommand(),
        TakeRoleCommand(),
        ToggleRoleCommand(),
        PrivateGainProfileCommand(),
        MassBanCommand(),
        MassUnbanCommand(),
        MassKickCommand(),
        BlurpleCommand(),
        BotBanCommand(),
        VintageCommand(),
        SpeedupGif(),
        BrightnessCommand(),
        JPGCommand(),
        PNGCommand(),
        WebpCommand(),
        RotateRightCommand(),
        RotateLeftCommand(),
        TrimCommand(),
        TakeCommand(),
        ContrastCommand(),
        KaleidoScopeCommand(),
        CalculateCommand(),
        ChannelFlagsCommand(),
        DiceCommand(),
        ManiaCommand(),
        TaikoCommand(),
        CatchTheBeatCommand(),
        YoshiCommand()
    )

    fun build(): CommandClient {
        return CommandClient(commands.toSet(), container)
    }

    fun loadCommands(): CommandClientBuilder {
        TaskManager.async {
            container.daoManager.commandWrapper.bulkInsert(commands)
        }
        logger.info("Loaded ${commands.size} commands")
        return this
    }
}