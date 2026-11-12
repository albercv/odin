package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.application.fake.Fixtures;
import com.e2d.odin.application.fake.InMemoryDocumentRepository;
import com.e2d.odin.application.fake.InMemoryOrganizationRepository;
import com.e2d.odin.application.fake.InMemoryTaggingRepository;
import com.e2d.odin.application.fake.InMemoryTopicRepository;
import com.e2d.odin.application.fake.MutableClock;
import com.e2d.odin.application.fake.ScriptedTopicTagger;
import com.e2d.odin.application.port.TaggerFailedException;
import com.e2d.odin.application.port.TaggerUnavailableException;
import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.application.shared.TaggingWindow;
import com.e2d.odin.domain.document.Document;
import com.e2d.odin.domain.document.DocumentPage;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.CompanyScope;
import com.e2d.odin.domain.topic.DocumentTagging;
import com.e2d.odin.domain.topic.DocumentTopic;
import com.e2d.odin.domain.topic.TaggingStatus;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import com.e2d.odin.domain.topic.TopicSource;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TagPendingDocumentsTest {

    private static final TaggingSettings SETTINGS = new TaggingSettings(TaggingWindow.always(ZoneOffset.UTC),
            Duration.ofMinutes(5), 6000, 3);

    private final InMemoryOrganizationRepository orgs = new InMemoryOrganizationRepository();
    private final InMemoryDocumentRepository docs = new InMemoryDocumentRepository();
    private final InMemoryTopicRepository topics = new InMemoryTopicRepository();
    private final InMemoryTaggingRepository tagging = new InMemoryTaggingRepository(orgs, docs, topics);
    private final ScriptedTopicTagger tagger = new ScriptedTopicTagger();
    private final MutableClock clock = new MutableClock();
    private final CompanyId company = Fixtures.company(orgs);
    private final Topic water = topic("Agua");
    private final Topic insurance = topic("Seguros");

    private Topic topic(String name) {
        Topic topic = new Topic(new TopicId(UUID.randomUUID()), company, name, "Definición de " + name);
        topics.save(topic);
        return topic;
    }

    private TagPendingDocuments processor(TaggingSettings settings) {
        return new TagPendingDocuments(tagging, tagger, settings, clock);
    }

    private TagPendingDocuments processor() {
        return processor(SETTINGS);
    }

    private Document document(String... pageTexts) {
        Document d = Fixtures.readyDocument(docs, company, "x");
        docs.replacePages(d.id(), java.util.stream.IntStream.range(0, pageTexts.length)
                .mapToObj(i -> new DocumentPage(d.id(), i + 1, pageTexts[i], "stub", "1")).toList());
        return d;
    }

    private DocumentTagging row(Document d) {
        return tagging.rowOf(d.id()).orElseThrow();
    }

    private List<TopicId> savedTopics(Document d) {
        return tagging.topicsOf(CompanyScope.all(), d.id()).stream().map(DocumentTopic::topicId).toList();
    }

    @Test
    void closedWindowClaimsNothing() {
        Document d = document("texto");
        tagger.answers("agua");
        TaggingWindow night = TaggingWindow.between(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneOffset.UTC);
        clock.advance(Duration.ofHours(2)); // 12:00 UTC

        assertThat(processor(new TaggingSettings(night, Duration.ofMinutes(5), 6000, 3)).processNext()).isFalse();

        assertThat(tagging.rowOf(d.id())).isEmpty();
        assertThat(tagger.received()).isEmpty();
    }

    @Test
    void unavailableTaggerClaimsNothing() {
        Document d = document("texto");
        tagger.available(false).answers("agua");

        assertThat(processor().processNext()).isFalse();

        assertThat(tagging.rowOf(d.id())).isEmpty();
        assertThat(tagger.received()).isEmpty();
    }

    @Test
    void nothingToTagReturnsFalse() {
        assertThat(processor().processNext()).isFalse();
    }

    @Test
    void unionOfChunkTopicsIsSaved() {
        Document d = document("a".repeat(60), "b".repeat(60));
        tagger.answers("agua").answers("seguros");

        assertThat(processor(new TaggingSettings(SETTINGS.window(), SETTINGS.chunkTimeout(), 80, 3)).processNext())
                .isTrue();

        assertThat(tagger.received()).hasSize(2);
        assertThat(savedTopics(d)).containsExactlyInAnyOrder(water.id(), insurance.id());
        assertThat(tagging.topicsOf(CompanyScope.all(), d.id())).allSatisfy(t -> {
            assertThat(t.source()).isEqualTo(TopicSource.AI);
            assertThat(t.taggerVersion()).contains(ScriptedTopicTagger.VERSION);
        });
        assertThat(row(d).status()).isEqualTo(TaggingStatus.DONE);
        assertThat(row(d).taggerVersion()).contains(ScriptedTopicTagger.VERSION);
    }

    @Test
    void instructionsCarryCompanyNameTopicsAndText() {
        document("Fuga en el garaje");
        tagger.answers("agua");

        processor().processNext();

        assertThat(tagger.received().get(0)).startsWith("Eres el sistema de archivo de Empresa ")
                .contains("- Agua: Definición de Agua").contains("Fuga en el garaje");
    }

    @Test
    void truncatedChunkIsSplitUntilItFits() {
        String text = ("palabra ".repeat(600)).strip();
        Document d = document(text);
        tagger.truncated().answers("agua").truncated().answers("seguros").answers("");

        assertThat(processor().processNext()).isTrue();

        // 1 entero (recortado) -> 2 mitades; la primera cabe, la segunda se recorta -> 2 cuartos que caben.
        assertThat(tagger.received()).hasSize(5);
        assertThat(tagger.received().get(1)).doesNotContain(text);
        assertThat(savedTopics(d)).containsExactlyInAnyOrder(water.id(), insurance.id());
        assertThat(row(d).status()).isEqualTo(TaggingStatus.DONE);
    }

    @Test
    void chunkBelowMinimumFailsWithListTooLong() {
        Document d = document("corto ".repeat(100));
        tagger.truncated();

        assertThat(processor().processNext()).isTrue();

        assertThat(tagger.received()).hasSize(1);
        assertThat(row(d).status()).isEqualTo(TaggingStatus.FAILED);
        assertThat(row(d).failureReason())
                .contains("La lista de temas es demasiado larga para el modelo: acorta las definiciones");
        assertThat(savedTopics(d)).isEmpty();
    }

    @Test
    void truncatedTextNeverSavesTopicsFromAnEarlierChunk() {
        Document d = document("a".repeat(60), "b ".repeat(30));
        tagger.answers("agua").truncated();

        processor(new TaggingSettings(SETTINGS.window(), SETTINGS.chunkTimeout(), 80, 3)).processNext();

        assertThat(row(d).status()).isEqualTo(TaggingStatus.FAILED);
        assertThat(savedTopics(d)).isEmpty();
    }

    @Test
    void noTopicFromListLeavesDoneWithoutTopics() {
        Document d = document("texto");
        tagger.answers("Jardinería; Piscinas");

        assertThat(processor().processNext()).isTrue();

        assertThat(row(d).status()).isEqualTo(TaggingStatus.DONE);
        assertThat(savedTopics(d)).isEmpty();
    }

    @Test
    void unavailableDuringTaggingReturnsWithoutSpendingAttempt() {
        Document d = document("texto");
        tagger.fails(new TaggerUnavailableException("sin conexión", new RuntimeException()));

        assertThat(processor().processNext()).isTrue();

        assertThat(row(d).status()).isEqualTo(TaggingStatus.PENDING);
        assertThat(row(d).attemptCount()).isZero();
    }

    @Test
    void failureReturnsToQueueSpendingAnAttemptUntilTheLast() {
        Document d = document("texto");
        tagger.fails(new TaggerFailedException("500"));

        processor().processNext();
        assertThat(row(d).status()).isEqualTo(TaggingStatus.PENDING);
        assertThat(row(d).attemptCount()).isEqualTo(1);
    }

    @Test
    void failureOnLastAttemptIsFailed() {
        Document d = document("texto");
        tagger.fails(new TaggerFailedException("500"));

        processor().processNext();
        processor().processNext();
        processor().processNext();

        assertThat(row(d).status()).isEqualTo(TaggingStatus.FAILED);
        assertThat(row(d).attemptCount()).isEqualTo(3);
        assertThat(row(d).failureReason()).contains("El etiquetador no pudo procesar el documento. Reinténtalo.");
        assertThat(processor().processNext()).isFalse();
    }

    @Test
    void fourthClaimIsTooManyAttempts() {
        Document d = document("texto");

        for (int i = 0; i < 3; i++) {
            tagging.claimNext(clock.instant(), SETTINGS);
            tagging.returnToQueue(d.id(), true);
        }
        assertThat(row(d).attemptCount()).isEqualTo(3);

        assertThat(processor().processNext()).isTrue();

        assertThat(row(d).status()).isEqualTo(TaggingStatus.FAILED);
        assertThat(row(d).failureReason()).contains("demasiados intentos");
        assertThat(tagger.received()).isEmpty();
    }
}
