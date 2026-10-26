package com.wq.caseware.senior;

import com.wq.caseware.senior.Domain.*;
import com.wq.caseware.senior.InMemoryAdapters.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class UpdateAwarenessServiceTest {
    UpdateAwarenessService svc;
    Principal firmA = new Principal("alice", Set.of("firm-A"));
    Principal firmB = new Principal("bob", Set.of("firm-B"));

    @BeforeEach void setup() {
        svc = new UpdateAwarenessService(new MemoryEngagements(), new MemoryCatalog(),
                new MembershipAuthorizer());
    }

    private EngagementEvent snapshot(String id, String firm, String applied,
                                     String decided, Decision decision, long revision) {
        return new EngagementEvent(id, firm, "E1", "tax", "CA", applied,
                decided, decision, revision);
    }

    @Test void publishedOfferCreatesPendingWithoutLoadingEngagement() {
        svc.onEngagementChanged(snapshot("e1", "firm-A", "v3", null, Decision.NONE, 1));
        svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v5", 5));
        var result = svc.pendingForFirm(firmA, "firm-A");
        assertEquals(1, result.size());
        assertEquals("v3", result.getFirst().fromVersion());
        assertEquals("v5", result.getFirst().offeredVersion());
    }

    @Test void declinePinsOnlyExactOfferAndAccumulatedDiffUsesAppliedBaseline() {
        svc.onEngagementChanged(snapshot("e1", "firm-A", "v3", null, Decision.NONE, 1));
        svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v5", 5));
        svc.onEngagementChanged(snapshot("e2", "firm-A", "v3", "v5", Decision.DECLINED, 2));
        assertTrue(svc.pendingForFirm(firmA, "firm-A").isEmpty());
        svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v7", 7));
        assertEquals("v3", svc.pendingForFirm(firmA, "firm-A").getFirst().fromVersion());
        assertEquals("v7", svc.pendingForFirm(firmA, "firm-A").getFirst().offeredVersion());
        assertEquals(1, svc.decisionsForEngagement(firmA, "firm-A", "E1").size());
    }

    @Test void appliedTemplateStopsBeingPending() {
        svc.onEngagementChanged(snapshot("e1", "firm-A", "v3", null, Decision.NONE, 1));
        svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v7", 7));
        svc.onEngagementChanged(snapshot("e2", "firm-A", "v7", "v7", Decision.APPLIED, 2));
        assertTrue(svc.pendingForFirm(firmA, "firm-A").isEmpty());
    }

    @Test void duplicateAndOutOfOrderEventsDoNotRegressProjectionOrDuplicateHistory() {
        var later = snapshot("e2", "firm-A", "v3", "v5", Decision.DECLINED, 2);
        assertTrue(svc.onEngagementChanged(later));
        assertFalse(svc.onEngagementChanged(later));
        assertFalse(svc.onEngagementChanged(snapshot("e1", "firm-A", "v3", null, Decision.NONE, 1)));
        svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v7", 7));
        assertFalse(svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v5", 5)));
        assertEquals("v7", svc.pendingForFirm(firmA, "firm-A").getFirst().offeredVersion());
        assertEquals(1, svc.decisionsForEngagement(firmA, "firm-A", "E1").size());
    }

    @Test void identicalEngagementIdCanExistInSeparateFirmsWithoutLeakage() {
        svc.onEngagementChanged(snapshot("a1", "firm-A", "v3", null, Decision.NONE, 1));
        svc.onEngagementChanged(snapshot("b1", "firm-B", "v5", null, Decision.NONE, 1));
        svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v5", 5));
        assertEquals(1, svc.pendingForFirm(firmA, "firm-A").size());
        assertEquals(0, svc.pendingForFirm(firmB, "firm-B").size());
        assertThrows(SecurityException.class, () -> svc.pendingForFirm(firmA, "firm-B"));
        assertThrows(SecurityException.class, () -> svc.decisionsForEngagement(firmB, "firm-A", "E1"));
    }

    @Test void marketSpecificOfferDoesNotAffectOtherMarket() {
        svc.onEngagementChanged(new EngagementEvent("e1", "firm-A", "E1", "tax", "EU", "v3", null, Decision.NONE, 1));
        svc.onTemplateOffered(new TemplateOffer("tax", "CA", "v5", 5));
        assertTrue(svc.pendingForFirm(firmA, "firm-A").isEmpty());
    }

    @Test void invalidSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> snapshot("e1", "firm-A", "v3", null, Decision.DECLINED, 1));
    }
}
