package com.e2d.odin.adapter.readers;

import java.util.List;

interface SheetRowSink {

    void startSheet(String name);

    void row(int rowNumber, List<String> cells);
}
