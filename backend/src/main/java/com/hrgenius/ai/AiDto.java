package com.hrgenius.ai;

import java.util.List;

/** API shapes for the Phase 16 AI endpoints. */
public class AiDto {

    /** Response for POST /ai/resume-skills and GET /ai/extract-employee/{id}. */
    public record ExtractionResponse(
            String source,
            List<String> skills,
            int count) {
    }

    /** One scored candidate for a job. */
    public record CandidateMatch(
            Long candidateId,
            String candidateName,
            String candidateEmail,
            String skills,
            List<String> matchedSkills,
            List<String> missingSkills,
            int score) {
    }

    /** Response for GET /ai/job-matches/{jobId}. */
    public record JobMatchResponse(
            Long jobId,
            String jobTitle,
            List<String> requiredSkills,
            List<CandidateMatch> matches) {
    }
}
