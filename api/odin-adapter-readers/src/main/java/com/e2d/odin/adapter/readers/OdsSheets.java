package com.e2d.odin.adapter.readers;

import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Lee un ods con StAX sobre content.xml: texto mostrado (text:p); las repeticiones solo se expanden con texto. */
final class OdsSheets {

    static final String MIMETYPE = "application/vnd.oasis.opendocument.spreadsheet";
    static final long MAX_CONTENT_BYTES = 200L * 1024 * 1024;
    private static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

    private final SheetRowSink sink;
    private final List<String> cells = new ArrayList<>();
    private int pendingEmpty;
    private int rowNumber;
    private int rowRepeat;
    private int cellRepeat;
    private final StringBuilder text = new StringBuilder();
    private int paragraphs;

    private OdsSheets(SheetRowSink sink) {
        this.sink = sink;
    }

    static void read(byte[] content, SheetRowSink sink) throws IOException, XMLStreamException {
        read(content, sink, MAX_CONTENT_BYTES);
    }

    static void read(byte[] content, SheetRowSink sink, long maxBytes) throws IOException, XMLStreamException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("content.xml".equals(entry.getName())) {
                    new OdsSheets(sink).parse(new Capped(zip, maxBytes));
                    return;
                }
            }
        }
        throw new UnsupportedFormatException("El ODS no tiene content.xml");
    }

    private void parse(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader xml = factory.createXMLStreamReader(in);
        while (xml.hasNext()) {
            switch (xml.next()) {
                case XMLStreamConstants.START_ELEMENT -> start(xml);
                case XMLStreamConstants.CHARACTERS -> text.append(xml.getText());
                case XMLStreamConstants.END_ELEMENT -> end(xml.getLocalName());
                default -> { }
            }
        }
    }

    private void start(XMLStreamReader xml) {
        switch (xml.getLocalName()) {
            case "table" -> sink.startSheet(attr(xml, TABLE, "name", ""));
            case "table-row" -> startRow(count(xml, "number-rows-repeated"));
            case "table-cell", "covered-table-cell" -> cellRepeat = count(xml, "number-columns-repeated");
            case "p" -> startParagraph();
            case "s" -> text.append(" ".repeat(count(xml, "c")));
            case "tab", "line-break" -> text.append(' ');
            default -> { }
        }
    }

    private void end(String name) {
        switch (name) {
            case "p" -> paragraphs++;
            case "table-cell", "covered-table-cell" -> endCell();
            case "table-row" -> endRow();
            default -> { }
        }
    }

    private void startRow(int repeat) {
        rowRepeat = repeat;
        cells.clear();
        pendingEmpty = 0;
    }

    private void startParagraph() {
        if (paragraphs > 0 && text.length() > 0) {
            text.append(' ');
        }
    }

    private void endCell() {
        String value = text.toString().strip();
        text.setLength(0);
        paragraphs = 0;
        if (value.isEmpty()) {
            pendingEmpty += cellRepeat;
            return;
        }
        for (; pendingEmpty > 0; pendingEmpty--) {
            cells.add("");
        }
        for (int i = 0; i < cellRepeat; i++) {
            cells.add(value);
        }
    }

    private void endRow() {
        for (int i = 0; i < rowRepeat; i++) {
            if (!cells.isEmpty()) {
                sink.row(rowNumber + 1 + i, new ArrayList<>(cells));
            }
        }
        rowNumber += rowRepeat;
    }

    private static int count(XMLStreamReader xml, String name) {
        String ns = "c".equals(name) ? TEXT : TABLE;
        return Math.max(1, Integer.parseInt(attr(xml, ns, name, "1")));
    }

    private static String attr(XMLStreamReader xml, String ns, String name, String fallback) {
        String value = xml.getAttributeValue(ns, name);
        return value == null ? fallback : value;
    }

    private static final class Capped extends FilterInputStream {
        private long left;

        Capped(InputStream in, long max) {
            super(in);
            this.left = max;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                spend(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = super.read(buffer, off, len);
            if (n > 0) {
                spend(n);
            }
            return n;
        }

        private void spend(long n) {
            left -= n;
            if (left < 0) {
                throw new UnsupportedFormatException("Hoja de cálculo demasiado grande");
            }
        }
    }
}
