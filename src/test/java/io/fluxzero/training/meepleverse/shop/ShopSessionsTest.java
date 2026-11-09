package io.fluxzero.training.meepleverse.shop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ShopSessionsTest {
    @TempDir Path directory;
    final ShopLogin.Member member = new ShopLogin.Member("account", "Robin", "csrf");

    @Test void staysSignedInOnDayTwoAndCanStillSignOut() throws Exception {
        var sessions = new ShopSessions(directory);
        var handle = sessions.create(member);
        Files.setLastModifiedTime(onlySessionFile(), FileTime.from(Instant.now().minus(Duration.ofDays(1))));
        var restarted = new ShopSessions(directory);
        assertEquals(member, restarted.find(handle));
        restarted.remove(handle);
        assertNull(sessions.find(handle));
    }

    @Test void oldCookieExpiresEvenIfTheBrowserKeepsSendingIt() throws Exception {
        var sessions = new ShopSessions(directory);
        var handle = sessions.create(member);
        Files.setLastModifiedTime(onlySessionFile(), FileTime.from(Instant.now().minus(Duration.ofDays(31))));
        assertNull(sessions.find(handle));
    }

    private Path onlySessionFile() throws Exception {
        try (var files = Files.list(directory)) {
            return files.findFirst().orElseThrow();
        }
    }
}
