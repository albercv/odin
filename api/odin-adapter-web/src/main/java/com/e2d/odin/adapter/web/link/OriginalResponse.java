package com.e2d.odin.adapter.web.link;

import com.e2d.odin.application.link.OriginalFile;
import com.e2d.odin.domain.document.DocumentFormat;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** El original tal cual se guardó, con las cabeceras que impiden que el navegador lo ejecute o lo guarde en caché. */
public final class OriginalResponse {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private OriginalResponse() {
    }

    /** Con {@code Resource}, Spring atiende también las peticiones por rangos del visor de PDF. */
    public static ResponseEntity<Resource> of(OriginalFile file) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(file))
                .contentType(mediaType(file));
        if (file.format() != DocumentFormat.PDF) {
            // el visor de PDF de Chromium no abre un PDF con «sandbox»; el resto sí lleva el aislamiento
            response.header("Content-Security-Policy", "sandbox");
        }
        return response.body(new ByteArrayResource(file.content()));
    }

    private static MediaType mediaType(OriginalFile file) {
        return switch (file.format()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case DOCX -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case PLAIN_TEXT, MARKDOWN -> MediaType.parseMediaType("text/plain;charset=UTF-8");
            case IMAGE -> imageType(file.content());
        };
    }

    private static MediaType imageType(byte[] content) {
        if (startsWith(content, PNG)) {
            return MediaType.IMAGE_PNG;
        }
        return startsWith(content, JPEG) ? MediaType.IMAGE_JPEG : MediaType.APPLICATION_OCTET_STREAM;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && java.util.Arrays.equals(content, 0, prefix.length, prefix, 0,
                prefix.length);
    }

    /** {@code filename} en ASCII para los clientes viejos y {@code filename*} en UTF-8 con el nombre verdadero. */
    private static String disposition(OriginalFile file) {
        String kind = file.format() == DocumentFormat.DOCX ? "attachment" : "inline";
        String ascii = Normalizer.normalize(file.filename(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]|[\"\\\\;]", "_");
        String utf8 = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20")
                .replace("*", "%2A");
        return kind + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + utf8;
    }
}
