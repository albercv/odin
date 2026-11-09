package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.IdGenerator;
import java.util.UUID;

public class SequenceIdGenerator implements IdGenerator {

    private long counter;

    @Override
    public UUID newId() {
        return new UUID(0, ++counter);
    }
}
