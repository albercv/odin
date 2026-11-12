package com.e2d.odin.application.topic;

import com.e2d.odin.domain.document.DocumentPage;
import java.util.ArrayList;
import java.util.List;

/** Parte el texto de un documento en tramos que caben en el contexto del etiquetador. */
public final class TaggingChunks {

    public static final int MIN_CHARACTERS = 1000;

    private TaggingChunks() {
    }

    /** Junta las páginas en orden hasta {@code maxCharacters}; una página más larga va sola, partida por el límite. */
    public static List<String> of(List<DocumentPage> pages, int maxCharacters) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (DocumentPage page : pages) {
            String text = page.text().strip();
            if (text.length() > maxCharacters) {
                flush(chunks, current);
                chunks.addAll(cut(text, maxCharacters));
            } else if (!text.isEmpty() && current.length() + 1 + text.length() > maxCharacters) {
                flush(chunks, current);
                current.append(text);
            } else if (!text.isEmpty()) {
                current.append(current.isEmpty() ? "" : "\n").append(text);
            }
        }
        flush(chunks, current);
        return chunks;
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private static List<String> cut(String text, int limit) {
        List<String> pieces = new ArrayList<>();
        String rest = text;
        while (rest.length() > limit) {
            int at = lastWhitespaceUpTo(rest, limit);
            pieces.add(rest.substring(0, at).strip());
            rest = rest.substring(at).strip();
        }
        pieces.add(rest);
        return pieces;
    }

    private static int lastWhitespaceUpTo(String text, int limit) {
        for (int i = limit; i > 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return limit;
    }

    /** Las dos mitades no bajarían del mínimo. */
    public static boolean canSplit(String chunk) {
        return chunk.length() >= 2 * MIN_CHARACTERS;
    }

    /** Dos mitades, por el espacio más cercano a la mitad; sin espacios, por la mitad exacta. */
    public static List<String> split(String chunk) {
        int middle = chunk.length() / 2;
        int at = middle;
        for (int distance = 0; distance < middle; distance++) {
            if (Character.isWhitespace(chunk.charAt(middle + distance))) {
                at = middle + distance;
                break;
            }
            if (Character.isWhitespace(chunk.charAt(middle - distance))) {
                at = middle - distance;
                break;
            }
        }
        return List.of(chunk.substring(0, at).strip(), chunk.substring(at).strip());
    }
}
