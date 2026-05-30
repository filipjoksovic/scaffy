import { type ChangeEvent, useMemo, useRef, useState } from "react";
import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
} from "recharts";
import {
  analyzePipeline,
  type AnalysisResponse,
  type CapabilityFinding,
  type DimensionAnalysis,
} from "../api/analyze";
import {
  AppFrame,
  Badge,
  Button,
  Card,
  Eyebrow,
  RecommendationsPanel,
  StateRow,
} from "../components";
import {
  capabilityMeta,
  collectFindings,
  countIssues,
  dimensionSummary,
  findingKey,
  ruleMeta,
  formatDimension,
  formatFileSize,
  formatProvider,
  formatScore,
  statusMeta,
  statusBadgeClassName,
  validateFile,
} from "../lib/analyzer";

type AnalyzeStatus =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "success"; report: AnalysisResponse }
  | { kind: "error"; message: string };

export function Analyzer() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [status, setStatus] = useState<AnalyzeStatus>({ kind: "idle" });

  const canAnalyze =
    file !== null && validationError === null && status.kind !== "loading";
  const issueCount = useMemo(() => {
    if (status.kind !== "success") return 0;
    return status.report.dimensions.reduce(
      (total, dimension) => total + countIssues(dimension),
      0,
    );
  }, [status]);

  function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null;
    setStatus({ kind: "idle" });
    setFile(selected);
    setValidationError(validateFile(selected));
  }

  function clearFile() {
    setFile(null);
    setValidationError(null);
    setStatus({ kind: "idle" });
    if (inputRef.current) inputRef.current.value = "";
  }

  async function analyze() {
    const error = validateFile(file);
    setValidationError(error);
    if (!file || error) return;

    setStatus({ kind: "loading" });
    try {
      const report = await analyzePipeline(file);
      setStatus({ kind: "success", report });
    } catch (err) {
      setStatus({
        kind: "error",
        message:
          err instanceof Error ? err.message : "Pipeline analysis failed.",
      });
    }
  }

  return (
    <AppFrame>
      <section aria-labelledby="analyzer-title" className="analyzer-band">
        <header className="page-header">
          <Eyebrow>Pipeline analyzer</Eyebrow>
          <h1 id="analyzer-title">
            Upload a pipeline and review its CI/CD maturity.
          </h1>
          <p>
            Analyze GitHub Actions or GitLab CI YAML files against build &amp;
            release, testing, workflow quality, security, and deployment
            capabilities.
          </p>
        </header>

        <div className="analyzer-layout">
          <Card as="section" className="upload-panel">
            <div>
              <Eyebrow>Upload</Eyebrow>
              <h2>Pipeline file</h2>
              <p>
                Use a single .yml or .yaml file. The backend performs provider
                detection and scoring.
              </p>
            </div>

            <label
              className={
                validationError ? "file-drop file-drop--error" : "file-drop"
              }
              htmlFor="pipeline-file"
            >
              <span>{file ? file.name : "Choose a pipeline YAML file"}</span>
              <small>
                {file
                  ? formatFileSize(file.size)
                  : "GitHub Actions and GitLab CI are supported."}
              </small>
              <input
                accept=".yml,.yaml"
                id="pipeline-file"
                onChange={selectFile}
                ref={inputRef}
                type="file"
              />
            </label>

            <div className="upload-actions">
              <Button disabled={!canAnalyze} onClick={analyze}>
                {status.kind === "loading"
                  ? "Analyzing..."
                  : "Analyze pipeline"}
              </Button>
              <Button
                disabled={!file && status.kind === "idle"}
                onClick={clearFile}
                variant="secondary"
              >
                Clear
              </Button>
            </div>

            {validationError && (
              <StateRow
                detail={validationError}
                icon="!"
                label="File cannot be analyzed"
                tone="error"
              />
            )}

            {status.kind === "loading" && (
              <StateRow
                detail="Calling /api/analyze with the selected YAML file."
                label="Analyzing pipeline"
                tone="loading"
              />
            )}
            {status.kind === "error" && (
              <StateRow
                detail={status.message}
                icon="!"
                label="Analysis failed"
                tone="error"
              />
            )}
          </Card>

          {status.kind === "success" ? (
            <ReportPanel issueCount={issueCount} report={status.report} />
          ) : (
            <Card as="section" className="report-empty">
              <Eyebrow>Report</Eyebrow>
              <h2>No analysis yet</h2>
              <p>
                The report will show an overall maturity rating, per-dimension
                capability scores, detected positives, smells, and missing
                signals.
              </p>
            </Card>
          )}
        </div>
      </section>
    </AppFrame>
  );
}

type ReportPanelProps = Readonly<{
  issueCount: number;
  report: AnalysisResponse;
}>;

function ReportPanel({ issueCount, report }: ReportPanelProps) {
  const [openSection, setOpenSection] = useState<string | null>("overall");
  const issueSuffix = issueCount === 1 ? "" : "s";

  function toggleSection(section: string) {
    setOpenSection((current) => (current === section ? null : section));
  }

  return (
    <section aria-label="Pipeline analysis report" className="report-panel">
      <Card
        as="section"
        className={
          openSection === "overall"
            ? "accordion-section accordion-section--open"
            : "accordion-section"
        }
      >
        <button
          aria-controls="overall-report-details"
          aria-expanded={openSection === "overall"}
          className="accordion-trigger score-card"
          onClick={() => toggleSection("overall")}
          type="button"
        >
          <div>
            <Eyebrow>Overall rating</Eyebrow>
            <div className="score-card__value">
              {formatScore(report.overallScore)}
            </div>
          </div>
          <div className="score-card__summary">
            <div className="score-card__meta">
              <Badge>{formatProvider(report.provider)}</Badge>
              <Badge>Level {report.overallLevel}</Badge>
              <StatusBadge status={report.overallStatus} />
            </div>
            <p>
              {issueCount === 0
                ? "No issues were reported across analyzed dimensions."
                : `${issueCount} issue${issueSuffix} found across analyzed dimensions.`}
            </p>
          </div>
          <span className="accordion-chevron" aria-hidden="true">
            {openSection === "overall" ? "Hide" : "Show"}
          </span>
        </button>
        {openSection === "overall" && (
          <div className="accordion-content" id="overall-report-details">
            <OverallDetail issueCount={issueCount} report={report} />
          </div>
        )}
      </Card>

      <div className="dimension-grid">
        {report.dimensions.map((dimension) => (
          <DimensionAccordion
            dimension={dimension}
            key={dimension.dimension}
            onToggle={() => toggleSection(dimension.dimension)}
            open={openSection === dimension.dimension}
          />
        ))}
      </div>

      <RecommendationsPanel report={report} />
    </section>
  );
}

type DimensionAccordionProps = Readonly<{
  dimension: DimensionAnalysis;
  onToggle: () => void;
  open: boolean;
}>;

function DimensionAccordion({
  dimension,
  onToggle,
  open,
}: DimensionAccordionProps) {
  const contentId = `${dimension.dimension}-details`;
  const notEvaluated = dimension.status === "not_evaluated";
  const positives = collectFindings(dimension, "POSITIVE");
  const smells = collectFindings(dimension, "SMELL");
  const missing = collectFindings(dimension, "MISSING");

  return (
    <Card
      as="section"
      className={
        open ? "accordion-section accordion-section--open" : "accordion-section"
      }
    >
      <button
        aria-controls={contentId}
        aria-expanded={open}
        className="accordion-trigger dimension-card"
        onClick={onToggle}
        type="button"
      >
        <div className="dimension-card__head">
          <div>
            <h3>{formatDimension(dimension.dimension)}</h3>
            <p>
              {notEvaluated
                ? statusMeta(dimension.status).label
                : `Level ${dimension.level} · ${statusMeta(dimension.status).label}`}
            </p>
          </div>
          <strong>{notEvaluated ? "—" : formatScore(dimension.score)}</strong>
        </div>
        {!notEvaluated && (
          <div aria-hidden="true" className="score-bar">
            <span style={{ width: formatScore(dimension.score) }} />
          </div>
        )}
        <div className="dimension-card__details">
          {notEvaluated ? (
            <span>No signals found in this pipeline for the dimension.</span>
          ) : (
            <>
              <span>{positives.length} positive</span>
              <span>
                {smells.length} smell{smells.length === 1 ? "" : "s"}
              </span>
              <span>{missing.length} missing</span>
            </>
          )}
        </div>
        <span className="accordion-chevron" aria-hidden="true">
          {open ? "Hide" : "Show"}
        </span>
      </button>
      {open && (
        <div className="accordion-content" id={contentId}>
          {notEvaluated ? (
            <StateRow
              detail="The ruleset for this dimension did not match any signals in the uploaded pipeline."
              label="Dimension not evaluated"
              tone="empty"
            />
          ) : (
            <DimensionDetail
              dimension={dimension}
              missing={missing}
              positives={positives}
              smells={smells}
            />
          )}
        </div>
      )}
    </Card>
  );
}

type OverallDetailProps = Readonly<{
  issueCount: number;
  report: AnalysisResponse;
}>;

function OverallDetail({ issueCount, report }: OverallDetailProps) {
  const radarData = report.dimensions
    .filter((dimension) => dimension.status !== "not_evaluated")
    .map((dimension) => ({
      dimension: formatDimension(dimension.dimension),
      score: Math.round(dimension.score * 100),
    }));

  return (
    <>
      <div className="section-title-row">
        <div>
          <Eyebrow>Dimension overview</Eyebrow>
          <h2>Report breakdown</h2>
        </div>
        <Badge>{issueCount} open</Badge>
      </div>
      <div className="overview-detail">
        <div className="radar-panel" aria-label="Dimension score spider graph">
          <ResponsiveContainer height={300} width="100%">
            <RadarChart data={radarData} outerRadius="72%">
              <PolarGrid stroke="var(--color-hairline-strong)" />
              <PolarAngleAxis
                dataKey="dimension"
                tick={{ fill: "var(--color-body)", fontSize: 12 }}
              />
              <PolarRadiusAxis
                angle={90}
                domain={[0, 100]}
                tick={false}
                axisLine={false}
              />
              <Radar
                dataKey="score"
                fill="var(--color-primary)"
                fillOpacity={0.18}
                stroke="var(--color-primary)"
                strokeWidth={2}
              />
            </RadarChart>
          </ResponsiveContainer>
        </div>

        <div className="overview-grid">
          {report.dimensions.map((dimension) => (
            <div className="overview-row" key={dimension.dimension}>
              <span>{formatDimension(dimension.dimension)}</span>
              <strong>
                {dimension.status === "not_evaluated"
                  ? "—"
                  : formatScore(dimension.score)}
              </strong>
              <small>{dimensionSummary(dimension)}</small>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

type DimensionDetailProps = Readonly<{
  dimension: DimensionAnalysis;
  missing: CapabilityFinding[];
  positives: CapabilityFinding[];
  smells: CapabilityFinding[];
}>;

function DimensionDetail({
  dimension,
  missing,
  positives,
  smells,
}: DimensionDetailProps) {
  return (
    <div className="detail-columns">
      <div className="detected-group">
        <h3>Positives ({positives.length})</h3>
        {positives.length > 0 ? (
          <ul>
            {positives.map((finding) => (
              <FindingListItem finding={finding} key={findingKey(finding)} />
            ))}
          </ul>
        ) : (
          <StateRow
            detail="No positive practices were detected for this dimension."
            label="No positives"
            tone="empty"
          />
        )}
      </div>

      <div className="issue-group">
        <h3>Smells ({smells.length})</h3>
        {smells.length > 0 ? (
          <ul>
            {smells.map((finding) => (
              <FindingListItem finding={finding} key={findingKey(finding)} />
            ))}
          </ul>
        ) : (
          <StateRow
            detail="No code smells were detected for this dimension."
            icon="✓"
            label="No smells"
            tone="success"
          />
        )}
      </div>

      <div className="issue-group">
        <h3>Missing ({missing.length})</h3>
        {missing.length > 0 ? (
          <ul>
            {missing.map((finding) => (
              <FindingListItem finding={finding} key={findingKey(finding)} />
            ))}
          </ul>
        ) : (
          <StateRow
            detail="All expected signals were detected for this dimension."
            icon="✓"
            label="Nothing missing"
            tone="success"
          />
        )}
      </div>

      <div className="detected-group">
        <h3>Capabilities ({dimension.capabilityScores.length})</h3>
        {dimension.capabilityScores.length > 0 ? (
          <ul>
            {dimension.capabilityScores.map((capability) => (
              <li key={capability.capability}>
                <strong>{capabilityMeta(capability.capability).label}</strong>
                <span>
                  {capability.points} / 4 points · {capability.findings.length}{" "}
                  finding{capability.findings.length === 1 ? "" : "s"}
                </span>
                <small>
                  {capabilityMeta(capability.capability).description}
                </small>
              </li>
            ))}
          </ul>
        ) : (
          <StateRow
            detail="No capabilities scored for this dimension."
            label="No capabilities"
            tone="empty"
          />
        )}
      </div>
    </div>
  );
}

type FindingListItemProps = Readonly<{ finding: CapabilityFinding }>;

function FindingListItem({ finding }: FindingListItemProps) {
  const rule = ruleMeta(finding.ruleId);

  return (
    <li>
      <strong>{rule.label}</strong>
      <small>{rule.description}</small>
      {finding.evidence && <span>{finding.evidence}</span>}
      {finding.location && <code>{finding.location}</code>}
    </li>
  );
}

type StatusBadgeProps = Readonly<{ status: string }>;

function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <Badge className={statusBadgeClassName(status)}>
      {statusMeta(status).label}
    </Badge>
  );
}
