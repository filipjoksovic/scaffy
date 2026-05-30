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

	private static final String FIX_SYSTEM_PROMPT = """
			You are a CI/CD pipeline review assistant for the Scaffy project.
			You receive a single analyzer finding about a GitHub Actions or GitLab CI pipeline together with the
			full workflow file (each line prefixed with its 1-based line number), and you produce a concrete fix
			for that one finding as a precise edit against that file.

			Hard requirements:
			- Reply with a single JSON object that matches this exact shape:
			  {
			    "summary": "Short imperative title for the fix (max 80 chars).",
			    "explanation": "1-3 sentences explaining what is wrong and how the edit fixes it.",
			    "language": "yaml",
			    "suggestedCode": "The same lines as edit.code, for display.",
			    "edit": {
			      "mode": "INSERT_AFTER" | "REPLACE",
			      "afterLine": <integer, required when mode is INSERT_AFTER>,
			      "startLine": <integer, required when mode is REPLACE>,
			      "endLine": <integer, required when mode is REPLACE>,
			      "code": "The exact YAML lines to insert or substitute."
			    }
			  }
			- The line numbers in "edit" refer to the numbered workflow file provided.
			- Use INSERT_AFTER to add new YAML: "afterLine" is the existing line number after which "code" is
			  inserted (use 0 to insert at the very top of the file).
			- Use REPLACE to change existing YAML: "startLine" and "endLine" are inclusive and replaced by "code".
			- "edit.code" MUST be indented exactly as it will appear in the final file. For example, a new job goes
			  under the top-level "jobs:" mapping and must be indented one level beneath it; a new step must align
			  with the existing steps of its job.
			- For a MISSING finding, INSERT_AFTER the most appropriate existing line so the new block nests correctly.
			- For a SMELL finding, REPLACE the offending lines with the corrected version.
			- For a POSITIVE finding, suggest a small hardening edit on top of what already works.
			- "code" must be valid pipeline YAML for the given provider. Do not invent unrelated changes.
			- Do not include any text, comments, or markdown fences outside the JSON object.
			""";

	public String systemPrompt() {
		return SYSTEM_PROMPT;
	}

	public String fixSystemPrompt() {
		return FIX_SYSTEM_PROMPT;
	}

	public String fixUserPrompt(FindingFixRequest request) {
		FindingFixRequest.Finding finding = request.finding();
		StringBuilder sb = new StringBuilder();
		sb.append("Pipeline provider: ").append(nullSafe(request.provider())).append('\n');
		sb.append("Workflow path: ").append(nullSafe(request.workflowPath())).append("\n\n");

		sb.append("Finding to fix:\n");
		sb.append("- rule: ").append(nullSafe(finding.ruleLabel()))
				.append(" (").append(nullSafe(finding.ruleId())).append(")\n");
		sb.append("- type: ").append(nullSafe(finding.type())).append('\n');
		sb.append("- dimension: ").append(nullSafe(finding.dimension())).append('\n');
		sb.append("- capability: ").append(nullSafe(finding.capability())).append('\n');
		if (finding.ruleDescription() != null && !finding.ruleDescription().isBlank()) {
			sb.append("- description: ").append(finding.ruleDescription()).append('\n');
		}
		if (finding.evidence() != null && !finding.evidence().isBlank()) {
			sb.append("- evidence: ").append(finding.evidence()).append('\n');
		}
		if (finding.location() != null && !finding.location().isBlank()) {
			sb.append("- location: ").append(finding.location()).append('\n');
		}
		if (finding.startLine() != null) {
			sb.append("- lines: ").append(finding.startLine());
			if (finding.endLine() != null && !finding.endLine().equals(finding.startLine())) {
				sb.append('-').append(finding.endLine());
			}
			sb.append('\n');
		}

		String content = request.workflowContent();
		if (content != null && !content.isBlank()) {
			sb.append("\nFull workflow file (each line prefixed with its 1-based line number):\n");
			sb.append("```\n").append(numberLines(content)).append("\n```\n");
		}

		sb.append("\nProduce the JSON object now. Do not include any text outside the JSON.");
		return sb.toString();
	}

	private String numberLines(String content) {
		String[] lines = content.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(String.format("%4d | %s", i + 1, lines[i]));
		}
		return sb.toString();
	}

	private String nullSafe(String value) {
		return value == null ? "" : value;
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
