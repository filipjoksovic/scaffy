package com.scaffy.backend.recommend;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.analyze.CapabilityFinding;
import com.scaffy.backend.analyze.CapabilityScore;
import com.scaffy.backend.analyze.DomainScore;
import com.scaffy.backend.analyze.FindingType;

@Component
public class RecommendationPromptBuilder {

	private static final String SYSTEM_PROMPT = """
			You are a CI/CD pipeline review assistant for the Scaffy project.
			Your job is to read a structured maturity analysis of a GitHub Actions or GitLab CI pipeline
			and produce a short, actionable list of improvement recommendations the engineer should make next.

			Hard requirements:
			- Reply with a single JSON object that matches this exact shape:
			  {
			    "recommendations": [
			      {
			        "title": "Short, imperative summary (max 80 chars).",
			        "description": "One or two sentences explaining what to add or change.",
			        "priority": "high" | "medium" | "low",
			        "reason": "Why this matters, tied to a concrete finding from the analysis input.",
			        "nextStep": "A single concrete next step the engineer can take in the YAML file."
			      }
			    ]
			  }
			- Return between 3 and 8 recommendations, ordered by priority (high first).
			- Base every recommendation on actual findings provided (positives, smells, or missing).
			  Do not invent issues that are not represented in the input.
			- Prefer recommendations that close a missing or smell finding before suggesting incremental improvements on a positive.
			- Keep recommendations specific to the pipeline file the engineer is editing.
			""";

	public String systemPrompt() {
		return SYSTEM_PROMPT;
	}

	public String userPrompt(AnalysisResponse analysis) {
		StringBuilder sb = new StringBuilder();
		sb.append("Pipeline provider: ").append(analysis.provider()).append('\n');
		sb.append("Overall score: ").append(analysis.overallScore())
				.append(" (level ").append(analysis.overallLevel())
				.append(", status ").append(analysis.overallStatus().value()).append(")\n\n");
		sb.append("Per-dimension analysis:\n");

		for (DomainScore dim : analysis.dimensions()) {
			sb.append("- ").append(dim.dimension())
					.append(" — score ").append(dim.score())
					.append(", level ").append(dim.level())
					.append(", status ").append(dim.status().value()).append('\n');
			for (CapabilityScore cap : dim.capabilityScores()) {
				sb.append("  * ").append(cap.capability())
						.append(" (").append(cap.points()).append(" / 4 pts)\n");
				renderFindings(sb, cap.findings(), FindingType.POSITIVE, "positive");
				renderFindings(sb, cap.findings(), FindingType.SMELL, "smell");
				renderFindings(sb, cap.findings(), FindingType.MISSING, "missing");
			}
		}

		sb.append("\nProduce the JSON object now. Do not include any text outside the JSON.");
		return sb.toString();
	}

	private void renderFindings(StringBuilder sb, List<CapabilityFinding> findings, FindingType type, String label) {
		String summary = findings.stream()
				.filter(f -> f.type() == type)
				.map(f -> {
					if (f.evidence() == null || f.evidence().isBlank()) {
						return f.ruleId();
					}
					return f.ruleId() + " (" + f.evidence() + ")";
				})
				.collect(Collectors.joining("; "));
		if (summary.isBlank()) {
			return;
		}
		sb.append("      ").append(label).append(": ").append(summary).append('\n');
	}
}
