package com.e2d.odin.adapter.postgres;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los documentos visibles de una cadena de sustituciones, de la más antigua a la vigente por la estructura de la
 * cadena (los saltos hasta el final). Es incompleta si se corta (el sustituto no se ve), si es circular, si se
 * ramifica (no hay un orden total) o si pasa del tope.
 */
final class VersionChain {

    static final int MAX_VERSIONS = 10;

    record Node(UUID id, String title, UUID supersededBy) {
    }

    private final List<Node> ordered;
    private final boolean incomplete;

    private VersionChain(List<Node> ordered, boolean incomplete) {
        this.ordered = ordered;
        this.incomplete = incomplete;
    }

    List<Node> ordered() {
        return ordered;
    }

    boolean incomplete() {
        return incomplete;
    }

    static VersionChain of(Collection<Node> nodes) {
        Map<UUID, Node> byId = new HashMap<>();
        nodes.forEach(n -> byId.put(n.id(), n));
        boolean broken = false;
        Map<UUID, Integer> depth = new HashMap<>();
        for (Node node : byId.values()) {
            int hops = 0;
            Node cursor = node;
            while (cursor.supersededBy() != null && hops <= byId.size()) {
                cursor = byId.get(cursor.supersededBy());
                if (cursor == null) {
                    broken = true;
                    break;
                }
                hops++;
            }
            broken |= hops > byId.size();
            depth.put(node.id(), hops);
        }
        List<Node> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.<Node>comparingInt(n -> -depth.get(n.id())).thenComparing(Node::title)
                .thenComparing(Node::id));
        boolean branched = depth.values().stream().distinct().count() < depth.size();
        boolean capped = sorted.size() > MAX_VERSIONS;
        List<Node> kept = capped ? sorted.subList(sorted.size() - MAX_VERSIONS, sorted.size()) : sorted;
        return new VersionChain(List.copyOf(kept), broken || branched || capped);
    }
}
