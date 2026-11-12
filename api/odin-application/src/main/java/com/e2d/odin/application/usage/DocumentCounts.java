package com.e2d.odin.application.usage;

public record DocumentCounts(int uploaded, int ready, int failed, int waitingForReader) {
}
