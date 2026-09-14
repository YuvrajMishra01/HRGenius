import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiResponse } from '../core/api.models';

/** Mirrors backend AI DTOs (Phase 16). */
export interface CandidateMatch {
  candidateId: number;
  candidateName: string;
  candidateEmail: string | null;
  skills: string | null;
  matchedSkills: string[];
  missingSkills: string[];
  score: number;
}

export interface JobMatchResponse {
  jobId: number;
  jobTitle: string;
  requiredSkills: string[];
  matches: CandidateMatch[];
}

export interface ExtractionResponse {
  source: string;
  skills: string[];
  count: number;
}

/** AI-assisted recruitment insights (Phase 16, deterministic engine). */
@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);

  jobMatches(jobId: number): Observable<ApiResponse<JobMatchResponse>> {
    return this.http.get<ApiResponse<JobMatchResponse>>(`/api/v1/ai/job-matches/${jobId}`);
  }

  resumeSkills(text: string): Observable<ApiResponse<ExtractionResponse>> {
    return this.http.post<ApiResponse<ExtractionResponse>>('/api/v1/ai/resume-skills', { text });
  }
}
