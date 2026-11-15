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
        return format == DocumentFormat.XLSX || format == DocumentFormat.XLS;
    }

    @Override
    public ReadResult read(byte[] content, DocumentFormat format) {
        SpreadsheetPager pager = new SpreadsheetPager();
        try {
            if (format == DocumentFormat.XLSX) {
                XlsxSheets.read(content, pager);
            } else {
                XlsSheets.read(content, pager);
            }
        } catch (UnsupportedFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedFormatException("No se pudo leer la hoja de cálculo");
        }
        return new ReadResult(pager.pages(), true, READER_NAME, READER_VERSION);
    }
}
