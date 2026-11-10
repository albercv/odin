package com.e2d.odin.bootstrap;

import java.sql.SQLException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Sustituye la traza por una explicación cuando PostgreSQL rechaza la clave (SQLSTATE 28P01). */
final class DatabaseCredentialsFailureAnalyzer extends AbstractFailureAnalyzer<SQLException> {

    private static final String WRONG_PASSWORD_STATE = "28P01";

    private static final String DESCRIPTION = "PostgreSQL rechaza la clave del usuario de la base de datos.";

    private static final String ACTION = "Las claves de la base se fijan en el primer arranque y no cambian al editar "
            + "deploy/.env. En una instalación nueva, borra los volúmenes (make down; docker volume rm odin_pgdata "
            + "odin_originals) y arranca otra vez. Si ya tiene datos, vuelve a poner la clave anterior o cámbiala en "
            + "PostgreSQL (README, «Claves de la base de datos»).";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SQLException cause) {
        if (!WRONG_PASSWORD_STATE.equals(cause.getSQLState())) {
            return null;
        }
        return new FailureAnalysis(DESCRIPTION, ACTION, cause);
    }
}
