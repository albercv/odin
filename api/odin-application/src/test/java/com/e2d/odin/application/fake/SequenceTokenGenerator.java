package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.TokenGenerator;

public class SequenceTokenGenerator implements TokenGenerator {

    private int counter;

    @Override
    public String randomToken(int bytes) {
        int length = (bytes * 4 + 2) / 3;
        return (++counter + "x".repeat(length)).substring(0, length);
    }
}
