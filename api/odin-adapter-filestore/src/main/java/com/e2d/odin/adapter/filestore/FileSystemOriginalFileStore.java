package com.e2d.odin.adapter.filestore;

import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.domain.shared.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public class FileSystemOriginalFileStore implements OriginalFileStore {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final Path root;

    public FileSystemOriginalFileStore(Path root) {
        this.root = root;
    }

    @Override
    public String store(byte[] content) {
        String sha256 = sha256Hex(content);
        Path target = pathOf(sha256);
        if (Files.exists(target)) {
            return sha256;
        }
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), sha256, ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return sha256;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] read(String sha256) {
        if (!SHA256.matcher(sha256).matches()) {
            throw new NotFoundException("Original no encontrado");
        }
        try {
            return Files.readAllBytes(pathOf(sha256));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Original no encontrado");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path pathOf(String sha256) {
        return root.resolve(sha256.substring(0, 2)).resolve(sha256);
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
