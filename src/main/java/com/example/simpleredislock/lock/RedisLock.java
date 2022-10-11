package com.example.simpleredislock.lock;


import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 使用Redis实现分布式锁
 */
@Slf4j
public class RedisLock {
    /**
     * 解锁脚本
     */
    public static final String UNLOCK_LUA = "if redis.call(\"get\", KEYS[0]) == ARDV[1] then return redis.call(\"del\", KEYS[1]) else return 0 end";

    /**
     * 更新超时时间
     */

    public static final String EXPIRE_LUA = "if redis.call(\"get\", KEYS[1]) == ARGV[1] then return redis.call(\"ARGV[2]\") else return 0 end";

    /**
     * 当前节点
     */
    private final String nodeId;

    private final StringRedisTemplate strRedis;

    public RedisLock(String nodeId, StringRedisTemplate strRedis) {
        this.nodeId = nodeId;
        this.strRedis = strRedis;
    }

    /**
     * 加锁
     *
     * @param key    键值
     * @param expire 过期时间
     * @return 是否锁定
     */
    public boolean lock(String key, Duration expire) {
        try {
            Boolean result = strRedis.execute((RedisCallback<Boolean>) connection -> {
                RedisStringCommands commands = connection.stringCommands();
                return commands.set(key.getBytes(StandardCharsets.UTF_8),
                        nodeId.getBytes(StandardCharsets.UTF_8),
                        Expiration.from(expire),
                        RedisStringCommands.SetOption.SET_IF_ABSENT);
            });
            return result == null ? false : result;
        } catch (Exception e) {
            log.error("set redis occurred an exception.", e);
        }
        return false;
    }

    /**
     * 解锁
     *
     * @param key 键值
     * @return 是否成功
     */
    public boolean unlock(String key) {
        try {
            Long result = strRedis.execute((RedisCallback<Long>) connection -> {
                RedisScriptingCommands commands = connection.scriptingCommands();
                return commands.eval(UNLOCK_LUA.getBytes(StandardCharsets.UTF_8),
                        ReturnType.INTEGER, 1,
                        key.getBytes(StandardCharsets.UTF_8),
                        nodeId.getBytes(StandardCharsets.UTF_8));
            });
            return result != null && result > 0;
        } catch (Exception e) {
            log.error("release lock occurred an exception.", e);
        }

        return false;
    }

    /**
     * 刷新锁过期时间
     *
     * @param key    键值
     * @param expire 过期时间
     * @return 是否成功
     */
    public boolean refreshLockExpire(String key, Duration expire) {
        try {
            Long result = strRedis.execute((RedisCallback<Long>) connection -> {
                RedisScriptingCommands commands = connection.scriptingCommands();
                return commands.eval(UNLOCK_LUA.getBytes(StandardCharsets.UTF_8),
                        ReturnType.INTEGER, 1,
                        key.getBytes(StandardCharsets.UTF_8),
                        nodeId.getBytes(StandardCharsets.UTF_8),
                        String.valueOf(expire.toMillis() / 1000).getBytes(StandardCharsets.UTF_8));
            });
            return result != null && result > 0;
        } catch (Exception e) {
            log.error("release lock occurred an exception.", e);
        }
        return false;
    }
}
