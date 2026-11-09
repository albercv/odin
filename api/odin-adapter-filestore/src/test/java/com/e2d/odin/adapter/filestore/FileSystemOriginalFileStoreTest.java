package com.e2d.odin.adapter.filestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.e2d.odin.domain.shared.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemOriginalFileStoreTest {

    @TempDir
    Path root;

    @Test
    void storeIsIdempotentAndAddressedBySha() throws Exception {
        FileSystemOriginalFileStore store = new FileSystemOriginalFileStore(root);
        byte[] content = "Documento ficticio".getBytes(StandardCharsets.UTF_8);

        String first = store.store(content);
        String second = store.store(content);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        assertThat(first).isEqualTo(expected).isEqualTo(second);
        assertThat(root.resolve(expected.substring(0, 2)).resolve(expected)).exists();
    }

    @Test
    void readReturnsSameBytes() {
        FileSystemOriginalFileStore store = new FileSystemOriginalFileStore(root);
        byte[] content = {1, 2, 3, 0, 127, -1};

        String sha = store.store(content);

        assertThat(store.read(sha)).isEqualTo(content);
    }

    @Test
    void readOfUnknownShaFails() {
        FileSystemOriginalFileStore store = new FileSystemOriginalFileStore(root);

        assertThatThrownBy(() -> store.read("ab" + "0".repeat(62))).isInstanceOf(NotFoundException.class);
    }
}
