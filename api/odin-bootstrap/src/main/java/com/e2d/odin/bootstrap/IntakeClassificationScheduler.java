package com.e2d.odin.bootstrap;

import com.e2d.odin.application.intake.ClassifyIntake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Carril de la clasificación de la primera carga: comparte el hilo de programación y no se solapa consigo mismo. */
@Component
public class IntakeClassificationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(IntakeClassificationScheduler.class);

    private final ClassifyIntake classify;

    public IntakeClassificationScheduler(ClassifyIntake classify) {
        this.classify = classify;
    }

    @Scheduled(fixedDelayString = "${odin.intake.classify-interval:PT5S}")
    void run() {
        try {
            classify.execute();
        } catch (RuntimeException e) {
            LOG.error("Fallo en la clasificación de la primera carga", e);
        }
    }
}
