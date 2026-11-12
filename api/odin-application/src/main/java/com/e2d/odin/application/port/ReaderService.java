package com.e2d.odin.application.port;

import com.e2d.odin.domain.document.DocumentFormat;

/** El servicio lector OCR, fuera de Odín: lee imágenes y PDF escaneados y devuelve el texto de cada página. */
public interface ReaderService {

    boolean isAvailable();

    /**
     * @throws ReaderUnavailableException no responde o no se puede conectar
     * @throws UnreadableDocumentException el fichero no se puede leer (415, 422)
     * @throws DocumentTooLargeForReaderException pasa del tamaño o de las páginas que admite (413)
     * @throws ReaderFailedException fallo del servicio, tiempo agotado o respuesta que no cumple el contrato
     */
    ReadResult read(byte[] content, DocumentFormat format);
}
