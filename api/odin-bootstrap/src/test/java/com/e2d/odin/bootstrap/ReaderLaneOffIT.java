package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.document.ProcessDocumentsWithReader;
import com.e2d.odin.application.shared.InstallationSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class ReaderLaneOffIT extends AbstractIT {

    @Autowired ApplicationContext context;
    @Autowired InstallationSettings installation;

    @Test
    void noUrlMeansNoReaderLaneBeans() {
        assertThat(context.getBeansOfType(ProcessDocumentsWithReader.class)).isEmpty();
        assertThat(context.getBeansOfType(ReaderProcessingScheduler.class)).isEmpty();
    }

    @Test
    void scannedDocumentsNotSupportedWithoutUrl() {
        assertThat(installation.scannedDocumentsSupported()).isFalse();
    }
}
