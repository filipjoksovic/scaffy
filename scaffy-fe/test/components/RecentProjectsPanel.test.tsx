// @vitest-environment jsdom
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { RecentProjectsPanel } from "../../src/components/RecentProjectsPanel";
import type { InitHistoryItem } from "../../src/api/init";

const historyItem: InitHistoryItem = {
  jobId: "job-1",
  projectName: "demo-app",
  stack: {
    frontend: "React",
    backend: "Spring Boot",
    pipeline: "GitHub Actions",
  },
  status: "succeeded",
  createdAt: "2026-01-01T12:00:00Z",
};

afterEach(() => {
  cleanup();
});

describe("RecentProjectsPanel", () => {
  it("shows a loading state", () => {
    const { getByText } = render(
      <RecentProjectsPanel error={null} items={[]} loading={true} />,
    );

    expect(getByText("Loading recent projects")).toBeTruthy();
  });

  it("shows an error state", () => {
    const { getByText } = render(
      <RecentProjectsPanel
        error="History unavailable"
        items={[]}
        loading={false}
      />,
    );

    expect(getByText("Could not load recent projects")).toBeTruthy();
    expect(getByText("History unavailable")).toBeTruthy();
  });

  it("shows an empty state", () => {
    const { getByText } = render(
      <RecentProjectsPanel error={null} items={[]} loading={false} />,
    );

    expect(getByText("No generated projects yet")).toBeTruthy();
  });

  it("renders recent project details", () => {
    const { getByText } = render(
      <RecentProjectsPanel error={null} items={[historyItem]} loading={false} />,
    );

    expect(getByText("demo-app")).toBeTruthy();
    expect(getByText(/React.*Spring Boot.*GitHub Actions/)).toBeTruthy();
    expect(getByText("succeeded")).toBeTruthy();
  });
});
