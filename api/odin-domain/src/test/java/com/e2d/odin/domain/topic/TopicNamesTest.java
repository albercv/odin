package com.e2d.odin.domain.topic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicNamesTest {

    @Test
    void containsIgnoresAccentsAndCase() {
        assertThat(TopicNames.contains("Agua y HUMEDADES", "agua")).isTrue();
        assertThat(TopicNames.contains("Instalación eléctrica", "instalacion  electrica")).isTrue();
    }

    @Test
    void differentNamesDoNotContain() {
        assertThat(TopicNames.contains("Agua", "agua y humedades")).isFalse();
        assertThat(TopicNames.contains("Seguros", "Ascensores")).isFalse();
    }
}
