package com.e2d.odin.application.fake;

import com.e2d.odin.application.port.AccessLinkRepository;
import com.e2d.odin.domain.link.AccessLink;
import com.e2d.odin.domain.shared.CompanyId;
import com.e2d.odin.domain.shared.UserId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryAccessLinkRepository implements AccessLinkRepository {

    private final Map<String, AccessLink> byHash = new LinkedHashMap<>();

    public synchronized List<String> hashes() {
        return List.copyOf(byHash.keySet());
    }

    public synchronized List<AccessLink> all() {
        return List.copyOf(byHash.values());
    }

    @Override
    public synchronized void save(AccessLink link, String tokenHash) {
        byHash.put(tokenHash, link);
    }

    @Override
    public synchronized Optional<AccessLink> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(byHash.get(tokenHash));
    }

    @Override
    public synchronized Optional<String> bind(AccessLink link, String address) {
        return find(link).map(l -> update(link, x -> x.boundAddress().isPresent() ? x : bound(x, address)))
                .flatMap(AccessLink::boundAddress);
    }

    private Optional<AccessLink> find(AccessLink link) {
        return byHash.values().stream().filter(l -> l.id().equals(link.id())).findFirst();
    }

    @Override
    public synchronized boolean reserveUpload(AccessLink link, int max) {
        AccessLink current = update(link, l -> l);
        if (current.uploadCount() >= max) {
            return false;
        }
        update(link, l -> counted(l));
        return true;
    }

    @Override
    public synchronized void deleteOf(CompanyId companyId, UserId userId) {
        byHash.values().removeIf(l -> l.companyId().equals(companyId) && l.userId().equals(userId));
    }

    private static AccessLink bound(AccessLink l, String address) {
        return new AccessLink(l.id(), l.kind(), l.companyId(), l.userId(), l.caseId(), l.documentId(), l.createdAt(),
                l.expiresAt(), Optional.of(address), l.uploadCount());
    }

    private static AccessLink counted(AccessLink l) {
        return new AccessLink(l.id(), l.kind(), l.companyId(), l.userId(), l.caseId(), l.documentId(), l.createdAt(),
                l.expiresAt(), l.boundAddress(), l.uploadCount() + 1);
    }

    private AccessLink update(AccessLink link, java.util.function.UnaryOperator<AccessLink> change) {
        for (var entry : byHash.entrySet()) {
            if (entry.getValue().id().equals(link.id())) {
                entry.setValue(change.apply(entry.getValue()));
                return entry.getValue();
            }
        }
        throw new IllegalStateException("Enlace no encontrado");
    }
}
