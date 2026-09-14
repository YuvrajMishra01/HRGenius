package com.hrgenius.ai;

import java.util.List;

import com.hrgenius.common.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI-assisted recruitment insights (Phase 16). Read-only, ADMIN/HR/MANAGER —
 * the same matrix as every other recruitment read. Everything here is
 * deterministic and explainable: a curated skill dictionary, word-boundary
 * matching, and a transparent score formula.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService service;
    private final SkillExtractor extractor;

    public AiController(AiService service, SkillExtractor extractor) {
        this.service = service;
        this.extractor = extractor;
    }

    /** Stateless skill extraction over pasted resume text. */
    @PostMapping("/resume-skills")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<AiDto.ExtractionResponse>> resumeSkills(
            @RequestBody ResumeTextRequest request) {
        var skills = extractor.extract(request.text());
        return ResponseEntity.ok(ApiResponse.of(new AiDto.ExtractionResponse(
                "pasted-text", List.copyOf(skills), skills.size())));
    }

    /** Stateless skill extraction over an uploaded resume file (.pdf/.txt). */
    @PostMapping(path = "/resume-files", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<AiDto.ExtractionResponse>> resumeFile(
            @RequestPart("file") MultipartFile file) throws java.io.IOException {
        String text = extractor.extractText(file.getOriginalFilename(), file.getBytes());
        var skills = extractor.extract(text);
        return ResponseEntity.ok(ApiResponse.of(new AiDto.ExtractionResponse(
                file.getOriginalFilename(), List.copyOf(skills), skills.size())));
    }

    public record ResumeTextRequest(String text) {
    }

    /** Scored candidate list for one job opening. */
    @GetMapping("/job-matches/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<AiDto.JobMatchResponse>> jobMatches(@PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.of(service.matchForJob(jobId)));
    }
}
