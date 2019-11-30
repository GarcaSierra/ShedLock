package net.javacrumbs.shedlock.core;

import org.junit.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LockAssertTest {

    @Test
    public void assertLockedShouldFailIfLockNotHeld() {
        assertThatThrownBy(LockAssert::assertLocked).hasMessageStartingWith("The task is not locked");
    }

    @Test
    public void assertLockedShouldNotFailIfLockHeld() {
        LockConfiguration lockConfiguration = new LockConfiguration("test", Instant.now().plusSeconds(10));

        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(lockConfiguration)).thenReturn(Optional.of(mock(SimpleLock.class)));

        new DefaultLockingTaskExecutor(lockProvider).executeWithLock(
            (Runnable) LockAssert::assertLocked,
            lockConfiguration
        );
    }

}