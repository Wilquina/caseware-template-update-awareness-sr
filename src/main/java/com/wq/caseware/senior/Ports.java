package com.wq.caseware.senior;

import java.util.List;
import java.util.Optional;
import com.wq.caseware.senior.Domain.*;

/** Infrastructure adapters must provide atomic revision-gated upserts. */
public final class Ports {
    private Ports() { }

    public interface EngagementStateStore {
        boolean acceptIfNewer(EngagementEvent event);
        Optional<EngagementEvent> find(String firmId, String engagementId);
        List<EngagementEvent> listByFirm(String firmId);
        List<DecisionAudit> decisionHistory(String firmId, String engagementId);
    }
    public interface TemplateCatalog {
        boolean acceptIfNewer(TemplateOffer offer);
        Optional<TemplateOffer> current(String templateId, String market);
    }
    public interface FirmAuthorizer {
        void requireAccess(Principal principal, String firmId);
    }
}
