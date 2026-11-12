package com.e2d.odin.application.topic;

import com.e2d.odin.application.port.TaggerAnswer;
import com.e2d.odin.application.port.TaggerUnavailableException;
import com.e2d.odin.application.port.TaggingJob;
import com.e2d.odin.application.port.TaggingRepository;
import com.e2d.odin.application.port.TopicTagger;
import com.e2d.odin.application.shared.TaggingSettings;
import com.e2d.odin.domain.shared.DocumentId;
import com.e2d.odin.domain.topic.TopicId;
import java.time.Clock;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Carril del etiquetado: pone los temas de la lista de la empresa a los documentos leídos, dentro de su ventana. */
public class TagPendingDocuments {

    public static final String LIST_TOO_LONG =
            "La lista de temas es demasiado larga para el modelo: acorta las definiciones";
    public static final String TAGGER_FAILED = "El etiquetador no pudo procesar el documento. Reinténtalo.";
    public static final String TOO_MANY_ATTEMPTS = "demasiados intentos";
    private static final String WITHOUT_TEXT = "sin texto";
    private static final System.Logger LOG = System.getLogger(TagPendingDocuments.class.getName());

    /** El tramo recortado ya no se puede partir más: no se guarda nada de este documento. */
    private static final class ListTooLongException extends RuntimeException {
    }

    private static final class Tagged {
        final Set<TopicId> topics = new HashSet<>();
        String taggerVersion = WITHOUT_TEXT;
    }

    private final TaggingRepository tagging;
    private final TopicTagger tagger;
    private final TaggingSettings settings;
    private final Clock clock;

    public TagPendingDocuments(TaggingRepository tagging, TopicTagger tagger, TaggingSettings settings, Clock clock) {
        this.tagging = tagging;
        this.tagger = tagger;
        this.settings = settings;
        this.clock = clock;
    }

    public boolean processNext() {
        if (!settings.window().isOpen(clock.instant()) || !tagger.isAvailable()) {
            return false;
        }
        Optional<TaggingJob> claimed = tagging.claimNext(clock.instant(), settings);
        claimed.ifPresent(this::process);
        return claimed.isPresent();
    }

    private void process(TaggingJob job) {
        DocumentId id = job.document().id();
        if (job.attemptCount() > settings.maxAttempts()) {
            tagging.markFailed(id, TOO_MANY_ATTEMPTS);
            return;
        }
        try {
            Tagged tagged = tagAll(job);
            tagging.saveResult(id, tagged.topics, tagged.taggerVersion, clock.instant());
        } catch (ListTooLongException e) {
            tagging.markFailed(id, LIST_TOO_LONG);
        } catch (TaggerUnavailableException e) {
            tagging.returnToQueue(id, false);
        } catch (RuntimeException e) {
            // el detalle va solo al log del servidor; el documento guarda un motivo genérico
            LOG.log(System.Logger.Level.WARNING, "El etiquetador no pudo procesar el documento " + id.value(), e);
            if (job.attemptCount() >= settings.maxAttempts()) {
                tagging.markFailed(id, TAGGER_FAILED);
            } else {
                tagging.returnToQueue(id, true);
            }
        }
    }

    private Tagged tagAll(TaggingJob job) {
        Tagged tagged = new Tagged();
        for (String chunk : TaggingChunks.of(job.pages(), settings.chunkCharacters())) {
            tagChunk(job, chunk, tagged);
        }
        return tagged;
    }

    /** Un tramo recortado se parte en dos y se reintenta; nunca se guardan temas de un texto que el modelo no leyó. */
    private void tagChunk(TaggingJob job, String chunk, Tagged tagged) {
        TaggerAnswer answer = tagger.tag(TaggingInstructions.build(job.companyName(), job.topics(), chunk));
        if (!answer.truncated()) {
            tagged.topics.addAll(TopicMatcher.match(answer.text(), job.topics()));
            tagged.taggerVersion = answer.taggerVersion();
        } else if (TaggingChunks.canSplit(chunk)) {
            TaggingChunks.split(chunk).forEach(half -> tagChunk(job, half, tagged));
        } else {
            throw new ListTooLongException();
        }
    }
}
