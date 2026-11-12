package com.e2d.odin.bootstrap;

import com.e2d.odin.application.topic.TagPendingDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Carril del etiquetado: comparte el hilo de programación y una ejecución no se solapa con la anterior. */
class TaggingScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TaggingScheduler.class);

    private final TagPendingDocuments tag;

    TaggingScheduler(TagPendingDocuments tag) {
        this.tag = tag;
    }

    @Scheduled(fixedDelayString = "${odin.tagger.poll-interval:PT60S}")
    void run() {
        try {
            while (tag.processNext()) {
                LOG.debug("Documento etiquetado");
            }
        } catch (RuntimeException e) {
            LOG.error("Fallo en el carril del etiquetado", e);
        }
    }
}
