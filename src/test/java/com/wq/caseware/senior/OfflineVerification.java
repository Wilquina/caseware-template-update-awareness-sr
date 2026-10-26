package com.wq.caseware.senior;

import com.wq.caseware.senior.Domain.*;
import com.wq.caseware.senior.InMemoryAdapters.*;
import java.util.Set;

/** Self-contained Java 21 smoke validation, no dependency download needed. */
public class OfflineVerification {
    private static int assertions;
    private static void check(boolean v, String why) { assertions++; if (!v) throw new AssertionError(why); }
    private static EngagementEvent ev(String id, String firm, String base, String decided, Decision decision, long rev) {
        return new EngagementEvent(id, firm, "E1", "tax", "CA", base, decided, decision, rev);
    }
    public static void main(String[] args) {
        var store = new MemoryEngagements();
        var catalog = new MemoryCatalog();
        var service = new UpdateAwarenessService(store, catalog, new MembershipAuthorizer());
        var a = new Principal("alice", Set.of("A"));
        var b = new Principal("bob", Set.of("B"));
        check(service.onEngagementChanged(ev("a1", "A", "v3", null, Decision.NONE, 1)), "ingest");
        check(service.onTemplateOffered(new TemplateOffer("tax", "CA", "v5", 5)), "publish");
        check(service.pendingForFirm(a, "A").getFirst().fromVersion().equals("v3"), "v3 baseline");
        check(service.onEngagementChanged(ev("a2", "A", "v3", "v5", Decision.DECLINED, 2)), "decline event");
        check(service.pendingForFirm(a, "A").isEmpty(), "declined v5 suppressed");
        check(!service.onEngagementChanged(ev("a2", "A", "v3", "v5", Decision.DECLINED, 2)), "duplicate blocked");
        check(!service.onEngagementChanged(ev("a1", "A", "v3", null, Decision.NONE, 1)), "stale event blocked");
        check(service.onTemplateOffered(new TemplateOffer("tax", "CA", "v7", 7)), "new offer");
        check(!service.onTemplateOffered(new TemplateOffer("tax", "CA", "v5", 5)), "stale offer blocked");
        check(service.pendingForFirm(a, "A").getFirst().offeredVersion().equals("v7"), "v7 pending");
        check(service.pendingForFirm(a, "A").getFirst().fromVersion().equals("v3"), "cumulative diff base");
        check(service.decisionsForEngagement(a, "A", "E1").size() == 1, "audit exactly once");
        check(service.onEngagementChanged(ev("b1", "B", "v7", null, Decision.NONE, 1)), "B ingest");
        check(service.pendingForFirm(b, "B").isEmpty(), "B isolated");
        try { service.pendingForFirm(a, "B"); throw new AssertionError("authorization bypass"); }
        catch (SecurityException expected) { assertions++; }
        service.onEngagementChanged(ev("a3", "A", "v7", "v7", Decision.APPLIED, 3));
        check(service.pendingForFirm(a, "A").isEmpty(), "applied not pending");
        System.out.println("PASS: " + assertions + " assertions (Java 21 standalone verification)");
    }
}
