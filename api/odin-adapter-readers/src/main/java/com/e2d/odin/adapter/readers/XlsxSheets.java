package com.e2d.odin.adapter.readers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.ss.usermodel.Comment;
import org.xml.sax.InputSource;

/** Lee un xlsx en streaming: valores mostrados y resultado guardado de las fórmulas. */
final class XlsxSheets {

    private XlsxSheets() {
    }

    static void read(byte[] content, SheetRowSink sink) throws Exception {
        DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-ES"));
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(content))) {
            XSSFReader reader = new XSSFReader(pkg);
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    sink.startSheet(sheets.getSheetName());
                    var xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(new XSSFSheetXMLHandler(reader.getStylesTable(),
                            reader.getSharedStringsTable(), new RowCollector(sink), formatter, false));
                    xml.parse(new InputSource(sheet));
                }
            }
        }
    }

    private static final class RowCollector implements SheetContentsHandler {
        private final SheetRowSink sink;
        private final List<String> cells = new ArrayList<>();

        RowCollector(SheetRowSink sink) {
            this.sink = sink;
        }

        @Override
        public void startRow(int rowNum) {
            cells.clear();
        }

        @Override
        public void endRow(int rowNum) {
            sink.row(rowNum + 1, new ArrayList<>(cells));
        }

        @Override
        public void cell(String reference, String value, Comment comment) {
            int column = new CellReference(reference).getCol();
            while (cells.size() <= column) {
                cells.add("");
            }
            cells.set(column, value == null ? "" : value);
        }
    }
}
