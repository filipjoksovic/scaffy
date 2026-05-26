import { describe, expect, it } from "vitest";
import type {
  CapabilityFinding,
  DimensionAnalysis,
} from "../../src/api/analyze";
import {
  capabilityMeta,
  collectFindings,
  countIssues,
  dimensionMeta,
  dimensionSummary,
  findingKey,
  findingTypeMeta,
  formatDimension,
  formatFileSize,
  formatLabel,
  formatProvider,
  formatScore,
  ruleMeta,
  statusMeta,
  statusBadgeClassName,
  validateFile,
} from "../../src/lib/analyzer";

function finding(
  overrides: Partial<CapabilityFinding> = {},
): CapabilityFinding {
  return {
    ruleId: "RULE_ID",
    dimension: "build_release",
    capability: "Build scripting",
    type: "POSITIVE",
    evidence: null,
    location: null,
    ...overrides,
  };
}

function dimension(
  overrides: Partial<DimensionAnalysis> = {},
): DimensionAnalysis {
  return {
    dimension: "build_release",
    score: 0.5,
    level: 3,
    status: "partial",
    capabilityScores: [],
    ...overrides,
  };
}

describe("collectFindings", () => {
  it("returns findings of the requested type across capabilities", () => {
    const dim = dimension({
      capabilityScores: [
        {
          capability: "Build scripting",
          points: 2,
          findings: [
            finding({ ruleId: "A", type: "POSITIVE" }),
            finding({ ruleId: "B", type: "SMELL" }),
          ],
        },
        {
          capability: "Packaging",
          points: 1,
          findings: [
            finding({ ruleId: "C", type: "POSITIVE" }),
            finding({ ruleId: "D", type: "MISSING" }),
          ],
        },
      ],
    });

    expect(collectFindings(dim, "POSITIVE").map((f) => f.ruleId)).toEqual([
      "A",
      "C",
    ]);
    expect(collectFindings(dim, "SMELL").map((f) => f.ruleId)).toEqual(["B"]);
    expect(collectFindings(dim, "MISSING").map((f) => f.ruleId)).toEqual(["D"]);
  });

  it("returns an empty array when no capabilities exist", () => {
    expect(collectFindings(dimension(), "POSITIVE")).toEqual([]);
  });
});

describe("countIssues", () => {
  it("sums smells and missing across all capabilities", () => {
    const dim = dimension({
      capabilityScores: [
        {
          capability: "a",
          points: 0,
          findings: [
            finding({ type: "POSITIVE" }),
            finding({ type: "SMELL" }),
            finding({ type: "SMELL" }),
            finding({ type: "MISSING" }),
          ],
        },
      ],
    });
    expect(countIssues(dim)).toBe(3);
  });

  it("returns zero when no findings exist", () => {
    expect(countIssues(dimension())).toBe(0);
  });
});

describe("findingKey", () => {
  it("uses ruleId, location and evidence", () => {
    expect(
      findingKey(
        finding({ ruleId: "X", location: "jobs.a", evidence: "npm test" }),
      ),
    ).toBe("X-jobs.a-npm test");
  });

  it("handles null location and evidence", () => {
    expect(findingKey(finding({ ruleId: "X" }))).toBe("X--");
  });
});

describe("validateFile", () => {
  it("accepts .yml files", () => {
    const file = new File([""], "ci.yml");
    expect(validateFile(file)).toBeNull();
  });

  it("accepts .yaml files", () => {
    const file = new File([""], "pipeline.yaml");
    expect(validateFile(file)).toBeNull();
  });

  it("rejects unsupported extensions", () => {
    const file = new File([""], "pipeline.txt");
    expect(validateFile(file)).toBe("Upload a .yml or .yaml pipeline file.");
  });

  it("returns null for missing file", () => {
    expect(validateFile(null)).toBeNull();
  });
});

describe("formatScore", () => {
  it("rounds to whole percent", () => {
    expect(formatScore(0.1234)).toBe("12%");
    expect(formatScore(0)).toBe("0%");
    expect(formatScore(1)).toBe("100%");
  });
});

describe("formatFileSize", () => {
  it("reports bytes under 1 KB", () => {
    expect(formatFileSize(500)).toBe("500 B");
  });

  it("reports KB up to 1 MB", () => {
    expect(formatFileSize(2048)).toBe("2 KB");
  });

  it("reports MB above 1 MB", () => {
    expect(formatFileSize(2 * 1024 * 1024)).toBe("2.0 MB");
  });
});

describe("formatProvider", () => {
  it("humanizes known providers", () => {
    expect(formatProvider("github-actions")).toBe("GitHub Actions");
    expect(formatProvider("gitlab-ci")).toBe("GitLab CI");
  });

  it("falls back to label formatting for unknown providers", () => {
    expect(formatProvider("jenkins-pipeline")).toBe("Jenkins Pipeline");
  });
});

describe("formatLabel and formatDimension", () => {
  it("title-cases snake_case", () => {
    expect(formatLabel("not_evaluated")).toBe("Not Evaluated");
  });

  it("title-cases kebab-case", () => {
    expect(formatDimension("workflow-quality")).toBe("Workflow Quality");
  });

  it("handles multiple whitespace and empty segments", () => {
    expect(formatLabel("build  release")).toBe("Build Release");
  });

  it("humanizes all-caps analyzer ids", () => {
    expect(formatLabel("BUILD_STAGE_PRESENT")).toBe("Build Stage Present");
  });

  it("uses known dimension labels", () => {
    expect(formatDimension("build_release")).toBe("Build & release management");
  });
});

describe("analyzer metadata", () => {
  it("returns labels and descriptions for known rule ids", () => {
    expect(ruleMeta("BUILD_STAGE_PRESENT")).toEqual({
      label: "Build stage present",
      description:
        "A recognizable build command or Docker build action was detected.",
    });
  });

  it("returns labels and descriptions for capabilities", () => {
    expect(capabilityMeta("Build scripting maturity").label).toBe(
      "Build scripting maturity",
    );
    expect(capabilityMeta("Build scripting maturity").description).toContain(
      "Build jobs",
    );
  });

  it("returns labels and descriptions for dimensions, statuses, and finding types", () => {
    expect(dimensionMeta("security_integration").label).toBe(
      "Security integration",
    );
    expect(statusMeta("not_evaluated").label).toBe("Not evaluated");
    expect(findingTypeMeta("SMELL").label).toBe("Needs review");
  });

  it("falls back to humanized labels for unknown backend ids", () => {
    expect(ruleMeta("SOME_NEW_RULE").label).toBe("Some New Rule");
    expect(statusMeta("new_status").label).toBe("New Status");
  });
});

describe("dimensionSummary", () => {
  it('reports "Not evaluated" for not_evaluated dimensions', () => {
    expect(dimensionSummary(dimension({ status: "not_evaluated" }))).toBe(
      "Not evaluated",
    );
  });

  it("reports issue count for evaluated dimensions", () => {
    const single = dimension({
      capabilityScores: [
        { capability: "a", points: 0, findings: [finding({ type: "SMELL" })] },
      ],
    });
    expect(dimensionSummary(single)).toBe("1 issue");

    const multi = dimension({
      capabilityScores: [
        {
          capability: "a",
          points: 0,
          findings: [finding({ type: "SMELL" }), finding({ type: "MISSING" })],
        },
      ],
    });
    expect(dimensionSummary(multi)).toBe("2 issues");

    expect(dimensionSummary(dimension())).toBe("0 issues");
  });
});

describe("statusBadgeClassName", () => {
  it("returns the not-evaluated class for not_evaluated", () => {
    expect(statusBadgeClassName("not_evaluated")).toBe(
      "badge badge--not-evaluated",
    );
  });

  it("returns undefined for other statuses", () => {
    expect(statusBadgeClassName("partial")).toBeUndefined();
    expect(statusBadgeClassName("complete")).toBeUndefined();
    expect(statusBadgeClassName("missing")).toBeUndefined();
  });
});
