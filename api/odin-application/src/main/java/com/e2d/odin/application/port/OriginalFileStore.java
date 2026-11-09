package com.e2d.odin.application.port;

public interface OriginalFileStore {

    String store(byte[] content);

    byte[] read(String sha256);
}
