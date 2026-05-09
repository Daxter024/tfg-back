package com.agro.terrainservice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del backend de almacenamiento local. Cubren los caminos de error
 * (path traversal) y el roundtrip store -> load -> delete.
 */
class LocalFileStorageServiceTest {

    @TempDir
    Path temp;

    private LocalFileStorageService storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new LocalFileStorageService(temp.toString());
        storage.init();
    }

    @AfterEach
    void cleanup() throws IOException {
        if (Files.exists(temp)) {
            try (Stream<Path> walk = Files.walk(temp)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("LocalFileStorage - store + load + delete roundtrip")
    void storeAndLoadAndDelete_roundtrip() throws IOException {
        byte[] data = "hello world".getBytes();
        UUID terrainId = UUID.randomUUID();

        String key = storage.store(new ByteArrayInputStream(data), data.length,
                terrainId.toString(), "doc.pdf");
        assertThat(key).isNotBlank();

        try (InputStream loaded = storage.load(key)) {
            assertThat(loaded.readAllBytes()).isEqualTo(data);
        }

        storage.delete(key);
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("LocalFileStorage - delete de clave inexistente no lanza")
    void delete_doesNotThrow_whenKeyMissing() {
        storage.delete("non-existent-key");
    }

    @Test
    @DisplayName("LocalFileStorage - sanitiza caracteres especiales en nombres")
    void store_sanitizesSpecialCharsInName() throws IOException {
        byte[] data = "x".getBytes();
        String key = storage.store(new ByteArrayInputStream(data), data.length,
                "tid", "weird name with spaces.pdf");
        // Espacios y caracteres peligrosos se reemplazan por _
        assertThat(key).doesNotContain(" ");
        // El fichero existe en disco bajo el root
        Path file = temp.resolve(key).normalize();
        assertThat(file.startsWith(temp)).isTrue();
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    @DisplayName("LocalFileStorage - load con clave que escapa al root lanza IOException")
    void load_rejectsTraversal() {
        assertThatThrownBy(() -> storage.load("../etc/passwd"))
                .isInstanceOf(IOException.class);
    }
}
