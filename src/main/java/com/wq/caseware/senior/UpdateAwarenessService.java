package com.wq.caseware.senior;

import com.wq.caseware.senior.Domain.*;
import com.wq.caseware.senior.Ports.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Tenant-scoped derived-state evaluation. */
public final class UpdateAwarenessService {
    private final EngagementStateStore engagements;
    private final TemplateCatalog templates;
    private final FirmAuthorizer authorization;

    public UpdateAwarenessService(EngagementStateStore engagements,
                                  TemplateCatalog templates,
                                  FirmAuthorizer authorization) {
        this.engagements = Objects.requireNonNull(engagements);
        this.templates = Objects.requireNonNull(templates);
        this.authorization = Objects.requireNonNull(authorization);
    }

    /** Trusted, region-local event ingress only; never expose directly to end users. */
    public boolean onEngagementChanged(EngagementEvent event) {
        return engagements.acceptIfNewer(event);
    }

    /** Global template metadata, filtered and distributed to relevant regions. */
    public boolean onTemplateOffered(TemplateOffer event) {
        return templates.acceptIfNewer(event);
    }

    /** Firm authorization precedes storage access; Postgres RLS is a second line of defense. */
    public List<PendingUpdate> pendingForFirm(Principal principal, String firmId) {
        authorization.requireAccess(principal, firmId);
        return engagements.listByFirm(firmId).stream()
                .map(this::evaluate)
                .flatMap(Optional::stream)
                .toList();
    }

    public List<DecisionAudit> decisionsForEngagement(
            Principal principal, String firmId, String engagementId) {
        authorization.requireAccess(principal, firmId);
        return engagements.decisionHistory(firmId, engagementId);
    }

    private Optional<PendingUpdate> evaluate(EngagementEvent state) {
        return templates.current(state.templateId(), state.market())
                .filter(offer -> !offer.targetVersion().equals(state.appliedVersion()))
                .filter(offer -> !(state.decision() == Decision.DECLINED
                        && offer.targetVersion().equals(state.decidedTargetVersion())))
                .map(offer -> new PendingUpdate(state.firmId(), state.engagementId(),
                        state.templateId(), state.appliedVersion(), offer.targetVersion()));
    }
}
