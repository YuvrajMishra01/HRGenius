package com.hrgenius.ai;

import java.util.List;
import java.util.Map;

/**
 * Curated skill dictionary for Phase 16. The workspace is self-contained by
 * design (see the Oracle-on-H2 decision), so "AI" here means a deterministic,
 * explainable matching engine: canonical skills with aliases, matched on word
 * boundaries. A real LLM provider could later implement the same two calls
 * (extract + match) behind {@link AiService} without touching the API.
 */
public final class SkillDictionary {

    /** Canonical skill → lowercase aliases, in stable display order. */
    public static final List<Map.Entry<String, List<String>>> SKILLS = List.of(
            Map.entry("Java", List.of("java")),
            Map.entry("Spring Boot", List.of("spring boot", "springboot", "spring")),
            Map.entry("Hibernate", List.of("hibernate")),
            Map.entry("Oracle SQL", List.of("oracle sql", "pl/sql", "oracle")),
            Map.entry("SQL", List.of("sql")),
            Map.entry("REST APIs", List.of("rest api", "rest apis", "restful", "rest")),
            Map.entry("Microservices", List.of("microservices", "microservice")),
            Map.entry("JUnit", List.of("junit")),
            Map.entry("Angular", List.of("angular")),
            Map.entry("React", List.of("react.js", "reactjs", "react")),
            Map.entry("TypeScript", List.of("typescript")),
            Map.entry("JavaScript", List.of("javascript", "es6")),
            Map.entry("HTML", List.of("html")),
            Map.entry("CSS", List.of("css")),
            Map.entry("Node.js", List.of("node.js", "nodejs", "node")),
            Map.entry("Python", List.of("python")),
            Map.entry("Docker", List.of("docker")),
            Map.entry("Kubernetes", List.of("kubernetes", "k8s")),
            Map.entry("Git", List.of("git")),
            Map.entry("AWS", List.of("aws")),
            Map.entry("Azure", List.of("azure")),
            Map.entry("Excel", List.of("excel")),
            Map.entry("Accounting", List.of("accounting", "bookkeeping")),
            Map.entry("Tally", List.of("tally")),
            Map.entry("Payroll", List.of("payroll")),
            Map.entry("Recruitment", List.of("recruitment", "talent acquisition", "sourcing")),
            Map.entry("Onboarding", List.of("onboarding")),
            Map.entry("Communication", List.of("communication", "stakeholder management")));

    private SkillDictionary() {
    }
}
