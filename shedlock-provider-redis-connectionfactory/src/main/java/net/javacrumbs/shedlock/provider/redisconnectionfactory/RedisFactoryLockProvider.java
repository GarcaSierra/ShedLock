/**
 * Copyright 2009-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.redisconnectionfactory;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking mechanism.
 * See https://redis.io/commands/set
 */
public class RedisFactoryLockProvider implements LockProvider {

    private static final String KEY_PREFIX = "job-lock";
    private static final String ENV_DEFAULT = "default";

    private RedisConnectionFactory redisConnectionFactory;
    private String environment;

    public RedisFactoryLockProvider(RedisConnectionFactory redisConn) {
        this(redisConn, ENV_DEFAULT);
    }

    public RedisFactoryLockProvider(RedisConnectionFactory redisConn, String environment) {
        this.redisConnectionFactory = redisConn;
        this.environment = environment;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {

        String key = buildKey(lockConfiguration.getName(), this.environment);
        RedisConnection redisConnection = null;
        try {
            redisConnection = redisConnectionFactory.getConnection();

            if (redisConnection.setNX(key.getBytes(), buildValue().getBytes())) {
                redisConnection.pExpire(key.getBytes(), getMsUntil(lockConfiguration.getLockAtMostUntil()));
                return Optional.of(new RedisLock(key, redisConnectionFactory, lockConfiguration));
            } else return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        } finally {
            if (redisConnection != null) redisConnection.close();
        }
    }

    private static final class RedisLock implements SimpleLock {

        private final String key;
        private final RedisConnectionFactory redisConnectionFactory;
        private final LockConfiguration lockConfiguration;

        private RedisLock(String key, RedisConnectionFactory redisConnectionFactory, LockConfiguration lockConfiguration) {
            this.key = key;
            this.redisConnectionFactory = redisConnectionFactory;
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            Expiration keepLockFor = Expiration.milliseconds(getMsUntil(lockConfiguration.getLockAtLeastUntil()));
            RedisConnection redisConnection = null;
            // lock at least until is in the past
            if (keepLockFor.getExpirationTimeInMilliseconds() <= 0) {
                try {
                    redisConnection = redisConnectionFactory.getConnection();
                    redisConnection.del(key.getBytes());
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                } finally {
                    if (redisConnection != null) redisConnection.close();
                }
            } else {
                try {
                    redisConnection = redisConnectionFactory.getConnection();
                    redisConnection.set(key.getBytes(), buildValue().getBytes(), keepLockFor, SetOption.SET_IF_PRESENT);
                } finally {
                    if (redisConnection != null) redisConnection.close();
                }
            }
        }
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(Instant.now(), instant).toMillis();
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown host";
        }
    }

    static String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", Instant.now().toString(), getHostname());
    }
}
