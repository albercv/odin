package com.e2d.odin.adapter.readers;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SpreadsheetTextReaderTest {

    private final SpreadsheetTextReader reader = new SpreadsheetTextReader();

    @Test
    void supportsXlsxAndXlsOnly() {
        assertThat(reader.supports(DocumentFormat.XLSX)).isTrue();
        assertThat(reader.supports(DocumentFormat.XLS)).isTrue();
        assertThat(reader.supports(DocumentFormat.DOCX)).isFalse();
    }

    @Test
    void readsOds() throws Exception {
        byte[] ods = OdsSheetsTest.ods("<table:table table:name=\"Hoja1\"><table:table-row><table:table-cell>"
                + "<text:p>dato</text:p></table:table-cell></table:table-row></table:table>");
        assertThat(reader.supports(DocumentFormat.ODS)).isTrue();
        assertThat(reader.read(ods, DocumentFormat.ODS).pageTexts()).containsExactly("Hoja «Hoja1»\ndato");
    }

    @Test
    void xlsxShowsDisplayedValuesAndHiddenSheets() throws Exception {
        ReadResult result = reader.read(book(new XSSFWorkbook()), DocumentFormat.XLSX);
        assertDisplayed(result);
    }

    @Test
    void xlsShowsDisplayedValuesAndHiddenSheets() throws Exception {
        ReadResult result = reader.read(book(new HSSFWorkbook()), DocumentFormat.XLS);
        assertDisplayed(result);
    }

    private static void assertDisplayed(ReadResult result) {
        assertThat(result.paginated()).isTrue();
        assertThat(result.readerName()).isEqualTo("odin-spreadsheet");
        assertThat(result.readerVersion()).isEqualTo("1");
        assertThat(result.pageTexts()).hasSize(2);
        assertThat(result.pageTexts().get(0)).startsWith("Hoja «Cuotas»\n")
                .contains("Ana\t15/03/2026\t1.234,50\t2.469,00\tsí");
        assertThat(result.pageTexts().get(1)).isEqualTo("Hoja «Oculta»\nsecreto");
    }

    private static byte[] book(Workbook wb) throws Exception {
        Sheet sheet = wb.createSheet("Cuotas");
        DataFormat fmt = wb.createDataFormat();
        CellStyle date = wb.createCellStyle();
        date.setDataFormat(fmt.getFormat("dd/mm/yyyy"));
        CellStyle money = wb.createCellStyle();
        money.setDataFormat(fmt.getFormat("#,##0.00"));
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("Ana");
        row.createCell(1).setCellValue(new Date(1_773_532_800_000L));
        row.getCell(1).setCellStyle(date);
        row.createCell(2).setCellValue(1234.5);
        row.getCell(2).setCellStyle(money);
        row.createCell(3).setCellFormula("C1*2");
        row.getCell(3).setCellStyle(money);
        row.createCell(4).setCellValue("sí");
        wb.createSheet("Oculta").createRow(0).createCell(0).setCellValue("secreto");
        wb.setSheetHidden(1, true);
        wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }
}
