package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.OriginalFileStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class InMemoryOriginalFileStore implements OriginalFileStore {

    private final Map<String, byte[]> files = new HashMap<>();

    public int size() {
        return files.size();
    }

    @Override
    public String store(byte[] content) {
        try {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            files.put(sha, content.clone());
            return sha;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] read(String sha256) {
        byte[] content = files.get(sha256);
        if (content == null) {
            throw new IllegalStateException("Original no encontrado");
        }
        return content.clone();
    }
}
