package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.PersonCodeGenerator;
import java.util.ArrayList;
import java.util.List;

/** Devuelve los códigos que se le dan, en orden; al acabarse, repite el último. Sin códigos, Q001, Q002... */
public class SequencePersonCodeGenerator implements PersonCodeGenerator {

    private final List<String> codes;
    private int calls;

    public SequencePersonCodeGenerator(String... codes) {
        this.codes = new ArrayList<>(List.of(codes));
    }

    @Override
    public String next() {
        calls++;
        if (codes.isEmpty()) {
            return String.format("Q%03d", calls);
        }
        return codes.get(Math.min(calls, codes.size()) - 1);
    }

    public int calls() {
        return calls;
    }
}
