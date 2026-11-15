package com.e2d.odin.adapter.readers;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/** Lee un xls con HSSF: valores mostrados y resultado guardado de las fórmulas. */
final class XlsSheets {

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("es-ES"));

    private XlsSheets() {
    }

    static void read(byte[] content, SheetRowSink sink) throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(content))) {
            for (Sheet sheet : workbook) {
                sink.startSheet(sheet.getSheetName());
                for (Row row : sheet) {
                    sink.row(row.getRowNum() + 1, cellsOf(row));
                }
            }
        }
    }

    private static List<String> cellsOf(Row row) {
        List<String> cells = new ArrayList<>();
        for (Cell cell : row) {
            while (cells.size() < cell.getColumnIndex()) {
                cells.add("");
            }
            cells.add(display(cell));
        }
        return cells;
    }

    private static String display(Cell cell) {
        if (cell.getCellType() != CellType.FORMULA) {
            return FORMATTER.formatCellValue(cell);
        }
        return switch (cell.getCachedFormulaResultType()) {
            case NUMERIC -> FORMATTER.formatRawCellContents(cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case ERROR -> FormulaError.forInt(cell.getErrorCellValue()).getString();
            default -> "";
        };
    }
}
