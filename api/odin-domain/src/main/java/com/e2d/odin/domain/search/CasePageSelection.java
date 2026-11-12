package com.e2d.odin.domain.search;

import java.util.ArrayList;
import java.util.List;

/** Qué páginas de un caso se devuelven en la misma respuesta. */
public final class CasePageSelection {

    public static final int MAX_PAGES = 3;
    public static final int MAX_CHARACTERS = 12_000;

    public record Selection(List<CasePage> pages, boolean truncated) {
    }

    private CasePageSelection() {
    }

    /**
     * Las páginas en orden desde la primera mientras no pasen de {@link #MAX_PAGES} ni de {@link #MAX_CHARACTERS}. Si la
     * mejor página se queda fuera, se añade al final aunque pase de los límites.
     */
    public static Selection select(List<CasePage> pagesInOrder, int bestPageNumber) {
        List<CasePage> chosen = new ArrayList<>();
        int characters = 0;
        for (CasePage page : pagesInOrder) {
            if (chosen.size() >= MAX_PAGES || characters + page.text().length() > MAX_CHARACTERS) {
                break;
            }
            chosen.add(page);
            characters += page.text().length();
        }
        if (chosen.stream().noneMatch(p -> p.number() == bestPageNumber)) {
            pagesInOrder.stream().filter(p -> p.number() == bestPageNumber).findFirst().ifPresent(chosen::add);
        }
        return new Selection(List.copyOf(chosen), chosen.size() < pagesInOrder.size());
    }
}
