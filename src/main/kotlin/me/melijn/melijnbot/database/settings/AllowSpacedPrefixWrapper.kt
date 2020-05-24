package me.melijn.melijnbot.database.settings

import com.google.common.cache.CacheBuilder
import me.melijn.melijnbot.database.IMPORTANT_CACHE
import me.melijn.melijnbot.objects.internals.TriState
import me.melijn.melijnbot.objects.threading.TaskManager
import me.melijn.melijnbot.objects.utils.loadingCacheFrom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class AllowSpacedPrefixWrapper(
    val taskManager: TaskManager,
    private val allowSpacedPrefixDao: AllowSpacedPrefixDao,
    private val privateAllowSpacedPrefixDao: PrivateAllowSpacedPrefixDao

) {

    val allowSpacedPrefixGuildCache = CacheBuilder.newBuilder()
        .expireAfterAccess(IMPORTANT_CACHE, TimeUnit.MINUTES)
        .build(loadingCacheFrom<Long, Boolean> { key ->
            containsGuild(key)
        })

    fun containsGuild(guildId: Long): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        taskManager.async {
            val result = allowSpacedPrefixDao.contains(guildId)
            future.complete(result)
        }
        return future
    }

    fun getUserTriState(userId: Long): CompletableFuture<TriState> {
        val future = CompletableFuture<TriState>()
        taskManager.async {
            val result = privateAllowSpacedPrefixDao.getState(userId)
            future.complete(result)
        }
        return future
    }

    suspend fun setGuildState(guildId: Long, state: Boolean) {
        if (state) allowSpacedPrefixDao.add(guildId)
        else allowSpacedPrefixDao.delete(guildId)
    }

    suspend fun setUserState(userId: Long, triState: TriState) {
        when (triState) {
            TriState.TRUE -> privateAllowSpacedPrefixDao.setState(userId, true)
            TriState.DEFAULT -> privateAllowSpacedPrefixDao.delete(userId)
            TriState.FALSE -> privateAllowSpacedPrefixDao.setState(userId, false)
        }
    }
}