package com.e2d.odin.application.usage;

import com.e2d.odin.domain.shared.UserId;
import java.util.Optional;

/**
 * Los dos únicos recuentos por persona. {@code memberCode} es {@code personCode-companyCode} y solo sale en el panel.
 * La fila de bajas no lleva ni persona ni código.
 */
public record PersonUsage(Optional<UserId> userId, String displayName, Optional<String> memberCode, int questions,
        int cardsSaved, boolean departed) {

    public static final String DEPARTED_LABEL = "Personas dadas de baja";

    public static PersonUsage departed(int questions, int cardsSaved) {
        return new PersonUsage(Optional.empty(), DEPARTED_LABEL, Optional.empty(), questions, cardsSaved, true);
    }
}
