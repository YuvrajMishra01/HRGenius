package com.hrgenius.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.hrgenius.recruitment.Candidate;
import com.hrgenius.recruitment.CandidateRepository;
import com.hrgenius.recruitment.Job;
import com.hrgenius.recruitment.JobRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Deterministic job↔candidate matching (Phase 16). A job's required skills
 * are extracted from title + description; each candidate's skills string is
 * extracted the same way, and the score is the simple, explainable share of
 * required skills the candidate has (floor percentage; 0 when the job text
 * yields no requirements). Ties break by fewer missing skills, then name.
 */
@Service
public class AiService {

    private final JobRepository jobs;
    private final CandidateRepository candidates;
    private final SkillExtractor extractor;

    public AiService(JobRepository jobs, CandidateRepository candidates, SkillExtractor extractor) {
        this.jobs = jobs;
        this.candidates = candidates;
        this.extractor = extractor;
    }

    @Transactional(readOnly = true)
    public AiDto.JobMatchResponse matchForJob(Long jobId) {
        Job job = jobs.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
        Set<String> required = extractor.extract(job.getTitle() + " "
                + (job.getDescription() == null ? "" : job.getDescription()));

        List<AiDto.CandidateMatch> matches = new ArrayList<>();
        for (Candidate candidate : candidates.findAll()) {
            Set<String> have = extractor.extract(candidate.getSkills() == null ? "" : candidate.getSkills());
            if (have.isEmpty()) {
                continue;
            }
            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (String skill : required) {
                (have.contains(skill) ? matched : missing).add(skill);
            }
            int score = required.isEmpty()
                    ? 0
                    : (int) Math.floor(100.0 * matched.size() / required.size());
            matches.add(new AiDto.CandidateMatch(candidate.getId(), candidate.getName(),
                    candidate.getEmail(), candidate.getSkills(), matched, missing, score));
        }
        matches.sort((a, b) -> {
            int byScore = Integer.compare(b.score(), a.score());
            if (byScore != 0) return byScore;
            int byMissing = Integer.compare(a.missingSkills().size(), b.missingSkills().size());
            if (byMissing != 0) return byMissing;
            return a.candidateName().compareToIgnoreCase(b.candidateName());
        });
        return new AiDto.JobMatchResponse(jobId, job.getTitle(),
                List.copyOf(required), List.copyOf(matches));
    }
}
