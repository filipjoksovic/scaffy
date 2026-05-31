// @vitest-environment jsdom
import { cleanup, fireEvent, render, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Dashboard } from "../../src/pages/Dashboard";
import {
  createInitJob,
  getInitCatalog,
  getInitHistory,
} from "../../src/api/init";
import {
  analyzeRepository,
  createRepositoryPublication,
  listRepositoryConnections,
} from "../../src/api/repositories";
import { listConnections } from "../../src/api/auth";

let authUser: {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
} | null = {
  id: "user-1",
  email: "dev@example.com",
  displayName: "Dev User",
  avatarUrl: null,
};

vi.mock("monaco-editor/esm/vs/editor/editor.worker.js?worker", () => ({
  default: class EditorWorker {},
}));

vi.mock("../../src/lib/auth", () => ({
  useAuth: () => ({
    user: authUser,
    loading: false,
    refresh: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock("../../src/lib/workspace", () => ({
  useWorkspace: () => ({
    workspaces: [
      {
        id: "workspace-1",
        name: "Scaffy Lab",
        role: "owner",
        createdAt: "2026-01-01T00:00:00Z",
      },
    ],
    activeWorkspace: {
      id: "workspace-1",
      name: "Scaffy Lab",
      role: "owner",
      createdAt: "2026-01-01T00:00:00Z",
    },
    selectWorkspace: vi.fn(),
    refresh: vi.fn(),
    loading: false,
  }),
}));

vi.mock("../../src/api/auth", () => ({
  connectProviderUrl: vi.fn((provider: string) => `/connect/${provider}`),
  instanceLoginUrl: vi.fn((registrationId: string) => `/oauth/${registrationId}`),
  listConnections: vi.fn(),
  listGitlabInstances: vi.fn(() => Promise.resolve([])),
  oauthLoginUrl: {
    github: "/oauth/github",
    gitlab: "/oauth/gitlab",
    google: "/oauth/google",
  },
}));

vi.mock("../../src/api/init", () => ({
  createInitJob: vi.fn(),
  getInitCatalog: vi.fn(),
  getInitHistory: vi.fn(),
  getInitJob: vi.fn(),
}));

vi.mock("../../src/api/repositories", () => ({
  analyzeRepository: vi.fn(),
  connectRepository: vi.fn(),
  createRepositoryPublication: vi.fn(),
  disconnectRepository: vi.fn(),
  getRepositoryAnalysis: vi.fn(),
  getRepositoryAnalysisDelta: vi.fn(),
  getRepositoryPublication: vi.fn(),
  listGitHubRepositories: vi.fn(),
  listRepositoryConnections: vi.fn(),
}));

function renderDashboard() {
  return render(
    <MemoryRouter>
      <Dashboard />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  authUser = {
    id: "user-1",
    email: "dev@example.com",
    displayName: "Dev User",
    avatarUrl: null,
  };
  vi.mocked(listConnections).mockResolvedValue([
    {
      provider: "github",
      instance: "github.com",
      displayName: "GitHub",
      scopes: [],
      connectedAt: "2026-01-01T00:00:00Z",
    },
  ]);
  vi.mocked(listRepositoryConnections).mockResolvedValue([]);
  vi.mocked(getInitHistory).mockResolvedValue([
    {
      jobId: "job-1",
      projectName: "demo-app",
      stack: {
        frontend: "React",
        backend: "Spring Boot",
        pipeline: "GitHub Actions",
      },
      status: "succeeded",
      createdAt: "2026-01-01T12:00:00Z",
    },
  ]);
  vi.mocked(getInitCatalog).mockResolvedValue({
    frontends: [
      {
        id: "react",
        name: "React",
        description: "React UI",
        defaultVersionId: "19",
        versions: [
          {
            id: "19",
            label: "19",
            version: "19",
            defaultRuntimeId: "node-22",
            runtimes: [
              {
                id: "node-22",
                label: "Node 22",
                runtime: "node",
                version: "22",
                lts: true,
              },
            ],
          },
        ],
      },
    ],
    backends: [
      {
        id: "spring-boot",
        name: "Spring Boot",
        description: "Spring API",
        defaultVersionId: "4",
        versions: [
          {
            id: "4",
            label: "4.0",
            version: "4.0",
            defaultRuntimeId: "java-21",
            runtimes: [
              {
                id: "java-21",
                label: "Java 21",
                runtime: "java",
                version: "21",
                lts: true,
              },
            ],
          },
        ],
      },
    ],
    pipelines: [
      {
        id: "github-actions",
        name: "GitHub Actions",
        description: "GitHub workflow",
      },
    ],
    maturityPresets: [
      {
        id: "l2",
        label: "Team",
        description: "Team defaults",
        level: 2,
        dockerRequired: false,
      },
    ],
  });
  vi.mocked(createInitJob).mockResolvedValue({
    jobId: "job-2",
    status: "succeeded",
    progress: "Done",
    errorMessage: null,
    selection: {
      frontend: {
        id: "react",
        name: "React",
        versionId: "19",
        versionLabel: "19",
        version: "19",
        runtimeId: "node-22",
        runtimeLabel: "Node 22",
        runtime: "node",
        runtimeVersion: "22",
      },
      backend: {
        id: "spring-boot",
        name: "Spring Boot",
        versionId: "4",
        versionLabel: "4.0",
        version: "4.0",
        runtimeId: "java-21",
        runtimeLabel: "Java 21",
        runtime: "java",
        runtimeVersion: "21",
      },
      pipeline: {
        id: "github-actions",
        name: "GitHub Actions",
      },
      pipelineMaturity: {
        id: "l2",
        label: "Team",
        description: "Team defaults",
        level: 2,
        dockerRequired: false,
      },
      includeDocker: false,
    },
    downloadAvailable: true,
    logs: [],
    createdAt: "2026-01-01T12:00:00Z",
    startedAt: null,
    completedAt: "2026-01-01T12:01:00Z",
  });
  vi.mocked(createRepositoryPublication).mockResolvedValue({
    publicationJobId: "pub-1",
    status: "succeeded",
    progress: "Published",
    errorMessage: null,
    provider: "github",
    repositoryName: "created-app",
    visibility: "private",
    repositoryOwner: "dev",
    repositoryUrl: "https://github.com/dev/created-app",
    repositoryConnection: {
      id: "repo-1",
      provider: "github",
      owner: "dev",
      name: "created-app",
      url: "https://github.com/dev/created-app",
      connectedAt: "2026-01-01T12:02:00Z",
      analysisRunCount: 0,
      analysisSummary: null,
    },
    logs: [],
    createdAt: "2026-01-01T12:01:00Z",
    startedAt: null,
    completedAt: "2026-01-01T12:02:00Z",
  });
  vi.mocked(analyzeRepository).mockRejectedValue(new Error("No token"));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Dashboard", () => {
  it("loads recent generated projects into the aside", async () => {
    const { getByLabelText, getByText } = renderDashboard();

    await waitFor(() => {
      expect(getInitHistory).toHaveBeenCalledWith();
    });

    expect(listRepositoryConnections).toHaveBeenCalled();
    expect(getByLabelText("Recent generated projects")).toBeTruthy();
    expect(getByText("Recent projects")).toBeTruthy();
    expect(getByText("demo-app")).toBeTruthy();
  });

  it("shows a recent-projects error when history cannot be loaded", async () => {
    vi.mocked(getInitHistory).mockRejectedValue(new Error("History unavailable"));

    const { getByText } = renderDashboard();

    await waitFor(() => {
      expect(getByText("Could not load recent projects")).toBeTruthy();
    });
    expect(getByText("History unavailable")).toBeTruthy();
  });

  it("does not load history while signed out", () => {
    authUser = null;

    renderDashboard();

    expect(getInitHistory).not.toHaveBeenCalled();
  });

  it("refreshes recent projects after creating a project", async () => {
    const { getAllByRole, getByLabelText, getByRole } = renderDashboard();

    fireEvent.click(getAllByRole("button", { name: "Create project" })[0]);
    await waitFor(() => {
      expect(getByLabelText("Repository name")).toBeTruthy();
    });

    fireEvent.change(getByLabelText("Repository name"), {
      target: { value: "created-app" },
    });
    fireEvent.click(getByRole("button", { name: "Generate project" }));

    await waitFor(() => {
      expect(createInitJob).toHaveBeenCalled();
    });
    fireEvent.click(getByRole("button", { name: "Publish to GitHub" }));

    await waitFor(() => {
      expect(createRepositoryPublication).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(getInitHistory).toHaveBeenCalledTimes(2);
    });
  });
});
