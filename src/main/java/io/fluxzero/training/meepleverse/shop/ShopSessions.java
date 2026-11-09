package io.fluxzero.training.meepleverse.shop;

import io.fluxzero.common.serialization.JsonUtils;
import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

record ShopSessions(Path directory) {
    static final Duration LIFETIME = Duration.ofDays(30);

    @SneakyThrows(IOException.class)
    String create(ShopLogin.Member member) {
        Files.createDirectories(directory);
        long cutoff = Instant.now().minus(LIFETIME).toEpochMilli();
        try (var files = Files.newDirectoryStream(directory, "*.json")) {
            for (var path : files) {
                if (path.toFile().lastModified() <= cutoff) Files.deleteIfExists(path);
            }
        }
        var handle = UUID.randomUUID().toString();
        Files.writeString(file(handle), JsonUtils.asJson(member), StandardOpenOption.CREATE_NEW);
        return handle;
    }

    @SneakyThrows(IOException.class)
    ShopLogin.Member find(String handle) {
        if (handle.isEmpty()) return null;
        var path = file(handle);
        try {
            if (Files.getLastModifiedTime(path).toInstant().plus(LIFETIME).isAfter(Instant.now())) {
                return JsonUtils.fromJson(Files.readString(path), ShopLogin.Member.class);
            }
            Files.deleteIfExists(path);
            return null;
        } catch (NoSuchFileException missing) {
            return null;
        }
    }

    @SneakyThrows(IOException.class)
    void remove(String handle) {
        Files.deleteIfExists(file(handle));
    }

    private Path file(String handle) {
        return directory.resolve(ShopLogin.sha256(handle) + ".json");
    }
}
