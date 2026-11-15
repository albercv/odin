package com.e2d.odin.adapter.readers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.e2d.odin.domain.shared.UnsupportedFormatException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class OdsSheetsTest {

    private record Row(int number, List<String> cells) { }

    private static final class Rows implements SheetRowSink {
        final List<String> sheets = new ArrayList<>();
        final List<Row> rows = new ArrayList<>();

        @Override
        public void startSheet(String name) {
            sheets.add(name);
        }

        @Override
        public void row(int rowNumber, List<String> cells) {
            rows.add(new Row(rowNumber, List.copyOf(cells)));
        }
    }

    static byte[] ods(String tables) throws Exception {
        return zip(OdsSheets.MIMETYPE, "<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
                + "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" "
                + "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">"
                + "<office:body><office:spreadsheet>" + tables + "</office:spreadsheet></office:body>"
                + "</office:document-content>");
    }

    static byte[] zip(String mimetype, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write(mimetype.getBytes(StandardCharsets.US_ASCII));
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static Rows read(String tables) throws Exception {
        Rows rows = new Rows();
        OdsSheets.read(ods(tables), rows);
        return rows;
    }

    @Test
    void twoSheetsWithTextParagraphsJoined() throws Exception {
        Rows rows = read("<table:table table:name=\"Uno\"><table:table-row><table:table-cell><text:p>a</text:p>"
                + "<text:p>b</text:p></table:table-cell><table:table-cell><text:p>c</text:p></table:table-cell>"
                + "</table:table-row></table:table><table:table table:name=\"Dos\"><table:table-row>"
                + "<table:table-cell><text:p>z</text:p></table:table-cell></table:table-row></table:table>");
        assertThat(rows.sheets).containsExactly("Uno", "Dos");
        assertThat(rows.rows).extracting(Row::cells).containsExactly(List.of("a b", "c"), List.of("z"));
    }

    @Test
    void repeatedTextCellIsExpandedAndEmptyRepeatsAreNot() throws Exception {
        Rows rows = read("<table:table table:name=\"S\"><table:table-row>"
                + "<table:table-cell table:number-columns-repeated=\"3\"><text:p>x</text:p></table:table-cell>"
                + "<table:table-cell table:number-columns-repeated=\"2\"/>"
                + "<table:table-cell><text:p>y</text:p></table:table-cell>"
                + "<table:table-cell table:number-columns-repeated=\"16384\"/></table:table-row>"
                + "<table:table-row table:number-rows-repeated=\"1000\"><table:table-cell "
                + "table:number-columns-repeated=\"16384\"/></table:table-row>"
                + "<table:table-row><table:table-cell><text:p>f</text:p></table:table-cell></table:table-row>"
                + "</table:table>");
        assertThat(rows.rows).extracting(Row::cells)
                .containsExactly(List.of("x", "x", "x", "", "", "y"), List.of("f"));
        assertThat(rows.rows).extracting(Row::number).containsExactly(1, 1002);
    }

    @Test
    void repeatedRowWithTextIsExpanded() throws Exception {
        Rows rows = read("<table:table table:name=\"S\"><table:table-row table:number-rows-repeated=\"2\">"
                + "<table:table-cell><text:p>r</text:p></table:table-cell></table:table-row></table:table>");
        assertThat(rows.rows).extracting(Row::number).containsExactly(1, 2);
    }

    @Test
    void oversizedContentFailsInsteadOfTruncating() throws Exception {
        byte[] bomb = zip(OdsSheets.MIMETYPE, "<a>" + "x".repeat(2_000_000) + "</a>");
        assertThatThrownBy(() -> OdsSheets.read(bomb, new Rows(), 1_000_000))
                .isInstanceOf(UnsupportedFormatException.class).hasMessage("Hoja de cálculo demasiado grande");
    }
}
