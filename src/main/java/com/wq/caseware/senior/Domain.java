package com.wq.caseware.senior;

import java.util.Objects;
import java.util.Set;

/** Pure contracts; these contain no engagement/workpaper or financial fields. */
public final class Domain {
    private Domain() { }

    public enum Decision { NONE, APPLIED, DECLINED }

    public record EngagementEvent(
            String eventId, String firmId, String engagementId,
            String templateId, String market, String appliedVersion,
            String decidedTargetVersion, Decision decision, long sourceRevision) {
        public EngagementEvent {
            require(eventId, "eventId"); require(firmId, "firmId");
            require(engagementId, "engagementId"); require(templateId, "templateId");
            require(market, "market"); require(appliedVersion, "appliedVersion");
            Objects.requireNonNull(decision);
            if (sourceRevision < 0) throw new IllegalArgumentException("negative source revision");
            if (decision != Decision.NONE) require(decidedTargetVersion, "decidedTargetVersion");
            if (decision == Decision.NONE && decidedTargetVersion != null)
                throw new IllegalArgumentException("NONE cannot carry a decision target");
        }
    }

    public record TemplateOffer(
            String templateId, String market, String targetVersion, long catalogRevision) {
        public TemplateOffer {
            require(templateId, "templateId"); require(market, "market");
            require(targetVersion, "targetVersion");
            if (catalogRevision < 0) throw new IllegalArgumentException("negative catalog revision");
        }
    }

    public record Principal(String userId, Set<String> authorizedFirmIds) {
        public Principal {
            require(userId, "userId"); authorizedFirmIds = Set.copyOf(authorizedFirmIds);
        }
    }

    public record PendingUpdate(
            String firmId, String engagementId, String templateId,
            String fromVersion, String offeredVersion) { }

    public record DecisionAudit(
            String eventId, String firmId, String engagementId, String targetVersion,
            Decision decision, long sourceRevision) { }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + name);
    }
}
