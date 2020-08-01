/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.consul;


import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.pszymczyk.consul.ConsulProcess;
import com.pszymczyk.consul.ConsulStarterBuilder;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static java.lang.Thread.sleep;
import static net.javacrumbs.shedlock.provider.consul.ConsulTtlLockProvider.MIN_TTL;
import static org.assertj.core.api.Assertions.assertThat;

class ConsulTtlLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    public static ConsulClient CONSUL_CLIENT;
    private static ConsulProcess consul;

    @BeforeAll
    public static void startConsul() {
        consul = ConsulStarterBuilder.consulStarter().build().start();
        CONSUL_CLIENT = new ConsulClient(consul.getAddress(), consul.getHttpPort());
    }

    @AfterAll
    public static void stopConsul() {
        consul.close();
    }

    @BeforeEach
    public void resetLockProvider() {
        consul.reset();
    }

    @Override
    protected LockProvider getLockProvider() {
        return new ConsulTtlLockProvider(CONSUL_CLIENT);
    }

    @Override
    protected void assertUnlocked(final String lockName) {
        GetValue leader = CONSUL_CLIENT.getKVValue(lockName + "-leader").getValue();
        assertThat(leader).isNull();
    }

    @Override
    protected void assertLocked(final String lockName) {
        GetValue leader = CONSUL_CLIENT.getKVValue(lockName + "-leader").getValue();
        assertThat(Optional.ofNullable(leader).map(GetValue::getSession)).isNotEmpty();
    }

    @Test
    @Disabled
    @Override
    public void fuzzTestShouldPass() {
        // fuzz test is disabled because it take too much time as it has atLeastFor
    }

    @Test
    @Override
    public void shouldTimeout() throws InterruptedException {
        // as consul has 10 seconds ttl minimum and has double ttl unlocking time, you have to wait for 20 seconds for the unlock time.
        Duration lockAtMostFor = Duration.ofSeconds(11);
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.multipliedBy(2).toMillis() + 100);
        assertUnlocked(LOCK_NAME1);

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofMillis(50), Duration.ZERO));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Test
    public void shouldNotTimeoutIfLessThanMinTtlPassed() throws InterruptedException {
        Duration lockAtMostFor = Duration.ofSeconds(1);
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.multipliedBy(2).toMillis() + 100);
        assertLocked(LOCK_NAME1);
    }

    @Test
    public void shouldWaitForMinTtlUntilUnlockedEvenIfLockAtLeastForIsLessThanMinTtl() throws InterruptedException {
        Duration lockAtLeastFor = Duration.ofSeconds(1);
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, Duration.ofMinutes(5), lockAtLeastFor);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();
        lock1.get().unlock();

        //even though more than atLeastFor passed, lock still should be in place
        long atLeastForWait = lockAtLeastFor.multipliedBy(2).toMillis() + 100;
        sleep(atLeastForWait);
        assertLocked(LOCK_NAME1);

        sleep(MIN_TTL.multipliedBy(2).toMillis() + 500 - atLeastForWait);
        assertUnlocked(LOCK_NAME1);
    }

    @Test
    public void shouldLockAtLeastFor() throws InterruptedException {
        // Lock for LOCK_AT_LEAST_FOR - we do not expect the lock to be released before this time
        Optional<SimpleLock> lock1 = getLockProvider().lock(lockConfig(LOCK_NAME1, LOCK_AT_LEAST_FOR.multipliedBy(2), LOCK_AT_LEAST_FOR));
        assertThat(lock1).isNotEmpty();
        lock1.get().unlock();

        // Even though we have unlocked the lock, it will be held for some time
        assertThat(getLockProvider().lock(lockConfig(LOCK_NAME1))).describedAs("Can not acquire lock, grace period did not pass yet").isEmpty();

        // we need to wait for at least minimum TTL*2 to unlock
        sleep(MIN_TTL.multipliedBy(2).toMillis() + 100);

        // Should be able to acquire now
        Optional<SimpleLock> lock3 = getLockProvider().lock(lockConfig(LOCK_NAME1));
        assertThat(lock3).describedAs("Can acquire the lock after grace period").isNotEmpty();
        lock3.get().unlock();
    }
}
