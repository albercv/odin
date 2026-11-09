package com.e2d.odin.bootstrap;

import com.e2d.odin.application.document.ProcessPendingDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentProcessingScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingScheduler.class);

    private final ProcessPendingDocuments process;

    public DocumentProcessingScheduler(ProcessPendingDocuments process) {
        this.process = process;
    }

    @Scheduled(fixedDelay = 2000)
    void run() {
        try {
            while (process.processNext()) {
                LOG.debug("Documento procesado");
            }
        } catch (RuntimeException e) {
            LOG.error("Fallo al procesar documentos pendientes", e);
        }
    }
}
