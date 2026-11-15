package com.e2d.odin.adapter.readers;

import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.util.ArrayList;
import java.util.List;

/** Convierte las filas de cada hoja en páginas de texto de como mucho MAX_ROWS filas y MAX_CHARS caracteres. */
class SpreadsheetPager implements SheetRowSink {

    static final int MAX_ROWS = 100;
    static final int MAX_CHARS = 10_000;
    static final int MAX_TOTAL_ROWS = 500_000;
    private static final int MAX_LINE = 4_000;
    private static final int MAX_HEADER = 2_000;

    private record Line(int number, String text) { }

    private static final class Chunk {
        final List<Line> lines = new ArrayList<>();
        int chars;
    }

    private static final class Sheet {
        final String name;
        final List<Chunk> chunks = new ArrayList<>();

        Sheet(String name) {
            this.name = name;
        }
    }

    private final List<Sheet> sheets = new ArrayList<>();
    private int totalRows;

    @Override
    public void startSheet(String name) {
        sheets.add(new Sheet(name));
    }

    @Override
    public void row(int rowNumber, List<String> cells) {
        String text = lineOf(cells);
        if (text.isEmpty()) {
            return;
        }
        if (++totalRows > MAX_TOTAL_ROWS) {
            throw new UnsupportedFormatException("Hoja de cálculo demasiado grande");
        }
        for (int from = 0; from < text.length(); from += MAX_LINE) {
            add(sheets.get(sheets.size() - 1), new Line(rowNumber, text.substring(from, Math.min(text.length(), from + MAX_LINE))));
        }
    }

    List<String> pages() {
        List<String> pages = new ArrayList<>();
        for (Sheet sheet : sheets) {
            for (int i = 0; i < sheet.chunks.size(); i++) {
                pages.add(render(sheet, i));
            }
        }
        return pages;
    }

    private static String lineOf(List<String> cells) {
        int last = cells.size() - 1;
        while (last >= 0 && cells.get(last).isBlank()) {
            last--;
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            kept.add(cells.get(i).replaceAll("[\\t\\r\\n]+", " "));
        }
        return String.join("\t", kept);
    }

    private static void add(Sheet sheet, Line line) {
        Chunk current = sheet.chunks.isEmpty() ? null : sheet.chunks.get(sheet.chunks.size() - 1);
        boolean full = current != null && (current.lines.size() >= MAX_ROWS
                || current.chars + line.text.length() + 1 > MAX_CHARS - reserved(sheet));
        if (current == null || full) {
            current = new Chunk();
            sheet.chunks.add(current);
        }
        current.lines.add(line);
        current.chars += line.text.length() + 1;
    }

    private static int reserved(Sheet sheet) {
        return sheet.name.length() + 100 + headerOf(sheet).length() + 1;
    }

    private static String headerOf(Sheet sheet) {
        String header = sheet.chunks.get(0).lines.get(0).text;
        return header.length() > MAX_HEADER ? "" : header;
    }

    private static String render(Sheet sheet, int index) {
        Chunk chunk = sheet.chunks.get(index);
        boolean split = sheet.chunks.size() > 1;
        StringBuilder text = new StringBuilder("Hoja «").append(sheet.name).append('»');
        if (split) {
            text.append(" (filas ").append(chunk.lines.get(0).number).append('–')
                    .append(chunk.lines.get(chunk.lines.size() - 1).number).append(')');
        }
        if (index > 0 && !headerOf(sheet).isEmpty()) {
            text.append('\n').append(headerOf(sheet));
        }
        chunk.lines.forEach(line -> text.append('\n').append(line.text));
        return text.toString();
    }
}
