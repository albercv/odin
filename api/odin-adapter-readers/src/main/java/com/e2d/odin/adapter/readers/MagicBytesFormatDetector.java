package com.e2d.odin.adapter.readers;

import com.e2d.odin.application.port.DocumentFormatDetector;
import com.e2d.odin.domain.document.DocumentFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MagicBytesFormatDetector implements DocumentFormatDetector {

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] TIFF_LITTLE = {'I', 'I', 42, 0};
    private static final byte[] TIFF_BIG = {'M', 'M', 0, 42};

    @Override
    public Optional<DocumentFormat> detect(byte[] content) {
        if (content.length == 0) {
            return Optional.empty();
        }
        if (startsWith(content, PDF)) {
            return Optional.of(DocumentFormat.PDF);
        }
        Optional<DocumentFormat> office = detectOffice(content);
        if (office.isPresent()) {
            return office;
        }
        if (isImage(content)) {
            return Optional.of(DocumentFormat.IMAGE);
        }
        return detectText(content);
    }

    private static boolean isImage(byte[] content) {
        return startsWith(content, PNG) || startsWith(content, JPEG)
                || startsWith(content, TIFF_LITTLE) || startsWith(content, TIFF_BIG);
    }

    private static Optional<DocumentFormat> detectOffice(byte[] content) {
        if (startsWith(content, ZIP)) {
            Set<String> names = zipEntryNames(content);
            if (names.contains("word/document.xml")) {
                return Optional.of(DocumentFormat.DOCX);
            }
            if (names.contains("xl/workbook.xml")) {
                return Optional.of(DocumentFormat.XLSX);
            }
            return isOds(content) ? Optional.of(DocumentFormat.ODS) : Optional.empty();
        }
        return startsWith(content, OLE2) && hasWorkbookStream(content)
                ? Optional.of(DocumentFormat.XLS) : Optional.empty();
    }

    private static boolean isOds(byte[] content) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry first = zip.getNextEntry();
            return first != null && "mimetype".equals(first.getName())
                    && "application/vnd.oasis.opendocument.spreadsheet"
                            .equals(new String(zip.readNBytes(100), StandardCharsets.US_ASCII).trim());
        } catch (IOException e) {
            return false;
        }
    }

    private static Set<String> zipEntryNames(byte[] content) {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        } catch (IOException e) {
            return names;
        }
        return names;
    }

    private static boolean hasWorkbookStream(byte[] content) {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(content))) {
            return fs.getRoot().hasEntry("Workbook") || fs.getRoot().hasEntry("Book");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static Optional<DocumentFormat> detectText(byte[] content) {
        if (containsNul(content)) {
            return Optional.empty();
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            boolean markdown = text.startsWith("#") || text.contains("\n#");
            return Optional.of(markdown ? DocumentFormat.MARKDOWN : DocumentFormat.PLAIN_TEXT);
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private static boolean containsNul(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }
}
