package com.e2d.odin.application.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.topic.Topic;
import com.e2d.odin.domain.topic.TopicId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopicMatcherTest {

    private static final CompanyId COMPANY = new CompanyId(UUID.randomUUID());
    private static final Topic WATER = topic("Agua y humedades");
    private static final Topic INSURANCE = topic("Seguros");
    private static final Topic ELECTRICITY = topic("Instalación eléctrica");

    private static Topic topic(String name) {
        return new Topic(new TopicId(UUID.randomUUID()), COMPANY, name, "definición");
    }

    @Test
    void matchesIgnoringAccentsAndCase() {
        var matched = TopicMatcher.match("AGUA  Y HUMEDADES; instalacion electrica", List.of(WATER, INSURANCE, ELECTRICITY));

        assertThat(matched).containsExactlyInAnyOrder(WATER.id(), ELECTRICITY.id());
    }

    @Test
    void ignoresNamesNotInList() {
        assertThat(TopicMatcher.match("Jardinería; Piscinas", List.of(WATER, INSURANCE))).isEmpty();
        assertThat(TopicMatcher.match("", List.of(WATER))).isEmpty();
    }
}
