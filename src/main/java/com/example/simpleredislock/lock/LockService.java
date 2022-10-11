package com.example.simpleredislock.lock;

import com.example.simpleredislock.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 并发Redis锁
 */
@Slf4j
public class LockService {
    private static final int LOCK_TIMEOUT = 600;

    private static final int LOCK_MAX_MIN = 600;

    public static final String LOCK_PREFIX = "lock:";

    private RedisLock redisLock;

    /**
     * 锁Key和最大超时时间
     */
    private final Map<String, LocalDateTime> locks = new ConcurrentHashMap<>();

    /**
     * Spring加载Bean时会执行此构造函数
     */
    public LockService(StringRedisTemplate redisTemplate) {
        redisLock = new RedisLock(Utils.uuidBase64(), redisTemplate);
    }

    /**
     * 根据key锁定资源
     *
     * @param key        键值
     * @param maxTimeout 过期时间
     * @return 是否锁定
     */
    public boolean simLock(String key, int maxTimeout) {
        log.info("Lock with key {} by / {}", key, maxTimeout);

        // 不限定时为10分钟
        if (maxTimeout <= 0) {
            maxTimeout = LOCK_TIMEOUT;
        }

        key = LOCK_PREFIX + key;
        boolean r = redisLock.lock(key, Duration.ofSeconds(maxTimeout));
        if (!r) {
            log.error("Lock {} end with result {}", key, r);
        }

        log.info("Lock end with result {}", r);

        return r;
    }

    public boolean simUnlock(String key) {
        log.info("Unlock key {}...", key);

        key = LOCK_PREFIX + key;
        boolean r = redisLock.unlock(key);
        if (!r) {
            log.error("Unlock {} end with result {}", key, r);
        }
        return r;
    }

    /**
     * 锁住订单
     */
    public boolean lockOrder(String orderId) {
        boolean rt = !StringUtils.isEmpty(orderId) && lock("order:" + orderId, LOCK_MAX_MIN);
        log.info("Lock order {} result {}", orderId, rt);
        return rt;
    }

    /**
     * 解锁订单
     */
    public void unlockOrder(String orderId) {
        if (StringUtils.isEmpty(orderId)) {
            return;
        }

        boolean rt = unlock("order:" + orderId);
         log.info("Unlock order {} result {}", orderId, rt);
    }

    /**
     * 根据Key锁定资源
     *
     * @param key        key
     * @param maxTimeout 最大超时时间，单位为秒
     * @return 是否成功
     */
    public boolean lock(String key, int maxTimeout) {
         log.info("Lock with key {} by / {}", key, maxTimeout);

        if (maxTimeout <= 0) {
            maxTimeout = LOCK_MAX_MIN * 10;
        }

        key = LOCK_PREFIX + key;
        boolean r = redisLock.lock(key, Duration.ofSeconds(LOCK_TIMEOUT));
        if (r) {
            locks.put(key, LocalDateTime.now().plusSeconds(maxTimeout));
        } else {
            log.error("Lock {} end with result {}", key, r);
        }

         log.info("Lock end with result {}", r);
        return r;
    }

    public boolean unlock(String key) {
        log.info("Unlock key {}...", key);

        locks.remove(LOCK_PREFIX + key);
        boolean r = redisLock.unlock(key);
        if (!r) {
            log.error("Unlock {} end with result {}", key, r);
        }
        return r;
    }

    /**
     * 刷新锁超时时间
     * locks：应该是Key的锁定时间，设置的不是最大锁定时间，这样如果进程挂了，别的进程就可以获取锁
     * 进程没挂之前，定期刷新锁过期时间
     */
    @Scheduled(fixedDelay = 50_000)
    public void refreshLock() {
        Iterator<Map.Entry<String, LocalDateTime>> iterator = locks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LocalDateTime> entry = iterator.next();
            // 超过最大锁定时间
            if (entry.getValue().isBefore(LocalDateTime.now())) {
                iterator.remove();
            }
            redisLock.refreshLockExpire(entry.getKey(), Duration.ofSeconds(LOCK_TIMEOUT));
        }
    }
}
