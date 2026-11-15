package com.e2d.odin.adapter.readers;

import com.e2d.odin.application.port.DocumentTextReader;
import com.e2d.odin.application.port.ReadResult;
import com.e2d.odin.domain.document.DocumentFormat;
import com.e2d.odin.domain.shared.UnsupportedFormatException;

public class SpreadsheetTextReader implements DocumentTextReader {

    static final String READER_NAME = "odin-spreadsheet";
    static final String READER_VERSION = "1";

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.XLSX || format == DocumentFormat.XLS || format == DocumentFormat.ODS;
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        SpreadsheetPager pager = new SpreadsheetPager();
        try {
            switch (format) {
                case XLSX -> XlsxSheets.read(content, pager);
                case ODS -> OdsSheets.read(content, pager);
                default -> XlsSheets.read(content, pager);
            }
        } catch (UnsupportedFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedFormatException("No se pudo leer la hoja de cálculo");
        }
        return new ReadResult(pager.pages(), true, READER_NAME, READER_VERSION);
    }
}
