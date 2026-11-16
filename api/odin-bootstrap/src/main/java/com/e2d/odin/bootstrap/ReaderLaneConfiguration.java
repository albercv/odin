package com.e2d.odin.bootstrap;

import com.e2d.odin.adapter.readerservice.HttpReaderService;
import com.e2d.odin.application.document.ProcessDocumentsWithReader;
import com.e2d.odin.application.port.DocumentRepository;
import com.e2d.odin.application.port.OriginalFileStore;
import com.e2d.odin.application.port.ReaderService;
import com.e2d.odin.application.shared.ProcessingSettings;
import com.e2d.odin.application.shared.ReaderSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El carril del lector existe solo si odin.reader.url tiene valor. */
@Configuration
@ConditionalOnExpression("!'${odin.reader.url:}'.isBlank()")
public class ReaderLaneConfiguration {

    @Bean
    ReaderService readerService(@Value("${odin.reader.url}") URI url,
            @Value("${odin.reader.timeout:PT30M}") Duration timeout,
            @Value("${odin.reader.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${odin.reader.token:}") String token) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        return new HttpReaderService(url, timeout, connectTimeout, Optional.of(token).filter(value -> !value.isBlank()),
                client);
    }

    @Bean
    ReaderSettings readerSettings(@Value("${odin.reader.timeout:PT30M}") Duration timeout,
            @Value("${odin.reader.retry-delay:PT10M}") Duration retryDelay,
            @Value("${odin.reader.max-retry-delay:PT1H}") Duration maxRetryDelay, ProcessingSettings processing) {
        return new ReaderSettings(timeout, processing.maxAttempts(), retryDelay, maxRetryDelay);
    }

    @Bean
    ProcessDocumentsWithReader processDocumentsWithReader(DocumentRepository documents, OriginalFileStore files,
            ReaderService reader, ReaderSettings settings, Clock clock) {
        return new ProcessDocumentsWithReader(documents, files, reader, settings, clock);
    }

    @Bean
    ReaderProcessingScheduler readerProcessingScheduler(ProcessDocumentsWithReader process) {
        return new ReaderProcessingScheduler(process);
    }
}
