package com.wq.caseware.senior;

import com.wq.caseware.senior.Domain.*;
import com.wq.caseware.senior.Ports.*;
import java.util.*;

/** Test-only adapters. Production has PostgreSQL + RLS / regional IAM controls. */
public final class InMemoryAdapters {
    private InMemoryAdapters() { }

    public static final class MemoryEngagements implements EngagementStateStore {
        private record Key(String firm, String engagement) { }
        private final Map<Key, EngagementEvent> data = new HashMap<>();
        private final Map<Key, List<DecisionAudit>> history = new HashMap<>();

        @Override public synchronized boolean acceptIfNewer(EngagementEvent event) {
            Key key = new Key(event.firmId(), event.engagementId());
            EngagementEvent previous = data.get(key);
            if (previous != null && event.sourceRevision() <= previous.sourceRevision()) return false;
            // Source owns the whole state snapshot. Preserve only explicitly supplied data.
            data.put(key, event);
            if (event.decision() != Decision.NONE) {
                history.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new DecisionAudit(event.eventId(), event.firmId(), event.engagementId(),
                                event.decidedTargetVersion(), event.decision(), event.sourceRevision()));
            }
            return true;
        }
        @Override public synchronized Optional<EngagementEvent> find(String firmId, String engagementId) {
            return Optional.ofNullable(data.get(new Key(firmId, engagementId)));
        }
        @Override public synchronized List<EngagementEvent> listByFirm(String firmId) {
            return data.values().stream().filter(s -> s.firmId().equals(firmId)).toList();
        }
        @Override public synchronized List<DecisionAudit> decisionHistory(String firmId, String engagementId) {
            return List.copyOf(history.getOrDefault(new Key(firmId, engagementId), List.of()));
        }
    }

    public static final class MemoryCatalog implements TemplateCatalog {
        private record Key(String template, String market) { }
        private final Map<Key, TemplateOffer> data = new HashMap<>();
        @Override public synchronized boolean acceptIfNewer(TemplateOffer offer) {
            Key key = new Key(offer.templateId(), offer.market());
            TemplateOffer old = data.get(key);
            if (old != null && old.catalogRevision() >= offer.catalogRevision()) return false;
            data.put(key, offer);
            return true;
        }
        @Override public synchronized Optional<TemplateOffer> current(String templateId, String market) {
            return Optional.ofNullable(data.get(new Key(templateId, market)));
        }
    }

    public static final class MembershipAuthorizer implements FirmAuthorizer {
        @Override public void requireAccess(Principal principal, String firmId) {
            Objects.requireNonNull(principal);
            if (!principal.authorizedFirmIds().contains(firmId)) {
                throw new SecurityException("not authorized for firm");
            }
        }
    }
}
