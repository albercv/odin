package com.e2d.odin.domain.intake;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semejanza de textos del estudio de la primera carga (textlib.py): tejas, Jaccard, MinHash de 64 funciones y 32 bandas.
 * Determinista: los coeficientes salen de una semilla fija (13), así que las firmas son las mismas en cada ejecución.
 */
public final class TextSimilarity {

    /** Una pareja de claves, con la menor primero. */
    public record IdPair(String first, String second) {
    }

    private static final int HASHES = 64;
    private static final int BANDS = 32;
    private static final long PRIME = (1L << 61) - 1;
    private static final long[][] COEFFICIENTS = coefficients();

    private TextSimilarity() {
    }

    /** Tejas de k palabras consecutivas; si hay k o menos palabras, una sola. */
    public static Set<String> shingles(String norm, int k) {
        String[] words = norm.isEmpty() ? new String[0] : norm.split(" ");
        Set<String> out = new LinkedHashSet<>();
        if (words.length <= k) {
            out.add(String.join(" ", words));
            return out;
        }
        for (int i = 0; i + k <= words.length; i++) {
            out.add(String.join(" ", Arrays.copyOfRange(words, i, i + k)));
        }
        return out;
    }

    public static double jaccard(Set<?> a, Set<?> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        long common = a.stream().filter(b::contains).count();
        return (double) common / (a.size() + b.size() - common);
    }

    /** Firma MinHash: un hash por teja y 64 funciones lineales (a*h+b mod primo). */
    public static long[] minHash(Set<String> shingles) {
        long[] base = shingles.stream().mapToLong(s -> Long.remainderUnsigned(hash64(s), PRIME)).toArray();
        long[] sig = new long[HASHES];
        for (int i = 0; i < HASHES; i++) {
            long min = Long.MAX_VALUE;
            for (long h : base) {
                min = Math.min(min, mulAddMod(COEFFICIENTS[i][0], h, COEFFICIENTS[i][1]));
            }
            sig[i] = min;
        }
        return sig;
    }

    /** Parejas de claves que coinciden en alguna de las 32 bandas de la firma. */
    public static Set<IdPair> lshCandidates(Map<String, long[]> signatures) {
        int rows = HASHES / BANDS;
        Map<String, List<String>> buckets = new HashMap<>();
        signatures.forEach((key, sig) -> {
            for (int b = 0; b < BANDS; b++) {
                String band = b + ":" + Arrays.toString(Arrays.copyOfRange(sig, b * rows, (b + 1) * rows));
                buckets.computeIfAbsent(band, x -> new ArrayList<>()).add(key);
            }
        });
        Set<IdPair> pairs = new HashSet<>();
        for (List<String> keys : buckets.values()) {
            for (int i = 0; i < keys.size(); i++) {
                for (int j = i + 1; j < keys.size(); j++) {
                    pairs.add(pair(keys.get(i), keys.get(j)));
                }
            }
        }
        return pairs;
    }

    static IdPair pair(String a, String b) {
        return a.compareTo(b) <= 0 ? new IdPair(a, b) : new IdPair(b, a);
    }

    private static long[][] coefficients() {
        long[][] out = new long[HASHES][2];
        for (int i = 0; i < HASHES; i++) {
            for (int j = 0; j < 2; j++) {
                out[i][j] = Long.remainderUnsigned(hash64("13-" + i + "-" + j), PRIME - 1) + 1;
            }
        }
        return out;
    }

    /** Los primeros 8 bytes de SHA-256 (el estudio usa blake2b, que el JDK no trae; solo importa que sea fijo). */
    private static long hash64(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(d).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** (a*h + b) mod (2^61-1) sin desbordar: 2^64 es 8 y 2^61 es 1 módulo el primo. */
    private static long mulAddMod(long a, long h, long b) {
        long hi = Math.multiplyHigh(a, h);
        long lo = a * h;
        long s = reduce((lo & PRIME) + (lo >>> 61) + (hi << 3));
        return reduce(s + b);
    }

    private static long reduce(long x) {
        long r = (x & PRIME) + (x >>> 61);
        return r >= PRIME ? r - PRIME : r;
    }
}
