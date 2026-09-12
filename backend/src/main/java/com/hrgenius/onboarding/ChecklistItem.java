package com.hrgenius.onboarding;

import java.util.List;

/** One checklist line item; {@code done} flags completion. */
public record ChecklistItem(String label, boolean done) {

    /** The standard onboarding checklist used for every new record. */
    public static List<ChecklistItem> defaultChecklist() {
        return List.of(
                new ChecklistItem("Offer letter signed", false),
                new ChecklistItem("Background verification completed", false),
                new ChecklistItem("ID proofs & documents collected", false),
                new ChecklistItem("Employee account & email created", false),
                new ChecklistItem("Workstation / equipment assigned", false),
                new ChecklistItem("Payroll & tax details submitted", false),
                new ChecklistItem("Policy & code-of-conduct briefing", false),
                new ChecklistItem("Team introduction & mentor assigned", false));
    }
}
