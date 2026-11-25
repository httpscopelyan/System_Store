package br.com.scopel.pfv.beta.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceGuardTests {

    @TempDir Path folder;

    @AfterEach
    void releaseLock() {
        SingleInstanceGuard.release();
    }

    @Test
    void theFirstInstanceStartsAndASecondOneIsRefused() {
        Path lockFile = folder.resolve("instance.lock");

        assertTrue(SingleInstanceGuard.acquire(lockFile));
        assertFalse(SingleInstanceGuard.acquire(lockFile));
    }

    @Test
    void anInstanceCanStartAgainAfterTheFirstOneReleasesTheLock() {
        Path lockFile = folder.resolve("instance.lock");
        SingleInstanceGuard.acquire(lockFile);

        SingleInstanceGuard.release();

        assertTrue(SingleInstanceGuard.acquire(lockFile));
    }
}
