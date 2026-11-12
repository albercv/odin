package com.e2d.odin.application.usage;

import java.util.List;
import java.util.Optional;

/**
 * Mínimos de personas para enseñar un dato agregado: con menos, el dato permitiría deducir qué hizo una persona. La
 * regla vale para la API entera, no solo para el panel.
 */
public final class Disclosure {

    public static final int MIN_PEOPLE_FOR_GAPS = 3;
    public static final int MIN_AUTHORS_FOR_CARD_BREAKDOWN = 3;
    public static final int MIN_DEPARTED_FOR_ROW = 2;
    public static final String WITHHELD_REASON = "FEW_PEOPLE";

    private Disclosure() {
    }

    /** Sin quien preguntara nada no se oculta nada: solo se oculta lo que existe y es de pocos. */
    public static GapReport gaps(List<GapGroup> groups, int askers) {
        if (askers > 0 && askers < MIN_PEOPLE_FOR_GAPS) {
            return new GapReport(List.of(), true);
        }
        return new GapReport(groups, false);
    }

    public static CardCounts cards(int saved, int pending, CardDecisions decisions, int authors) {
        if (authors < MIN_AUTHORS_FOR_CARD_BREAKDOWN) {
            return new CardCounts(saved, pending, Optional.empty(), saved > 0);
        }
        return new CardCounts(saved, pending, Optional.of(decisions), false);
    }

    public static Optional<PersonUsage> departedRow(int departedPeople, int questions, int cardsSaved) {
        return departedPeople < MIN_DEPARTED_FOR_ROW ? Optional.empty()
                : Optional.of(PersonUsage.departed(questions, cardsSaved));
    }
}
