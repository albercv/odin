package com.e2d.odin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

class DatabaseCredentialsFailureAnalyzerTest {

    private final DatabaseCredentialsFailureAnalyzer analyzer = new DatabaseCredentialsFailureAnalyzer();

    @Test
    void wrongPasswordStateGivesActionableAnalysis() {
        FailureAnalysis analysis = analyzer.analyze(new IllegalStateException(new SQLException("x", "28P01")));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).isEqualTo("PostgreSQL rechaza la clave del usuario de la base de datos.");
        assertThat(analysis.getAction()).contains("docker volume rm odin_pgdata odin_originals");
    }

    @Test
    void otherSqlStatesAreIgnored() {
        assertThat(analyzer.analyze(new IllegalStateException(new SQLException("x", "08001")))).isNull();
    }
}
