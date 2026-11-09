package com.e2d.odin.bootstrap;

import com.e2d.odin.application.port.IdGenerator;
import java.util.UUID;

public class RandomUuidGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
