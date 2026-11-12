package com.e2d.odin.bootstrap;

import com.e2d.odin.application.document.ProcessDocumentsWithReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Carril del lector: una ejecución no se solapa con la anterior (fixedDelay). */
class ReaderProcessingScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ReaderProcessingScheduler.class);

    private final ProcessDocumentsWithReader process;

    ReaderProcessingScheduler(ProcessDocumentsWithReader process) {
        this.process = process;
    }

    @Scheduled(fixedDelayString = "${odin.reader.poll-interval:PT30S}")
    void run() {
        try {
            while (process.processNext()) {
                LOG.debug("Documento leído por el lector");
            }
        } catch (RuntimeException e) {
            LOG.error("Fallo en el carril del lector", e);
        }
    }
}
