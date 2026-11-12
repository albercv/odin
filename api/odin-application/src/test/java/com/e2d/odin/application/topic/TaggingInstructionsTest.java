package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaggingInstructionsTest {

    @Test
    void buildsMeasuredInstructionsLiterally() {
        CompanyId company = new CompanyId(UUID.randomUUID());
        List<Topic> topics = List.of(
                new Topic(new TopicId(UUID.randomUUID()), company, "agua", "fugas, humedades y filtraciones"),
                new Topic(new TopicId(UUID.randomUUID()), company, "seguros", "pólizas y siniestros"));

        String instructions = TaggingInstructions.build("Fincas Sol", topics, "Texto del documento.");

        assertThat(instructions).isEqualTo("""
                Eres el sistema de archivo de Fincas Sol. Lee este documento y asígnale una o varias etiquetas de esta lista:\s
                - agua: fugas, humedades y filtraciones
                - seguros: pólizas y siniestros.

                Texto del documento.

                Si dudas, pon todas las que encajen: es peor que falte una etiqueta que poner una de más. Responde solo con las etiquetas que correspondan, separadas por punto y coma.""");
    }
}
