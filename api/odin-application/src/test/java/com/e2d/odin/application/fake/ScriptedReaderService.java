package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.application.port.ReaderService;
import com.e2d.odin.domain.document.DocumentFormat;
import java.util.ArrayList;
import java.util.List;

/** Lector con guion: cada lectura devuelve o lanza lo siguiente del guion, y la última entrada se repite. */
public class ScriptedReaderService implements ReaderService {

    private final List<Object> script = new ArrayList<>();
    private boolean available = true;
    private int reads;

    public ScriptedReaderService available(boolean value) {
        available = value;
        return this;
    }

    public ScriptedReaderService returns(String... pageTexts) {
        script.add(new ReadResult(List.of(pageTexts), true, "unlimited-ocr-2", "1"));
        return this;
    }

    public ScriptedReaderService fails(RuntimeException error) {
        script.add(error);
        return this;
    }

    public int reads() {
        return reads;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        Object next = script.get(Math.min(reads++, script.size() - 1));
        if (next instanceof RuntimeException error) {
            throw error;
        }
        return (ReadResult) next;
    }
}
