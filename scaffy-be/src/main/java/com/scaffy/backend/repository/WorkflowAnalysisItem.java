package com.scaffy.backend.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scaffy.backend.analyze.AnalysisResponse;
import com.scaffy.backend.repository.metrics.WorkflowMetricsResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowAnalysisItem(
        String workflowPath,
        AnalysisResponse analysis,
        WorkflowMetricsResult workflowMetrics,
        String errorMessage) {

    public static WorkflowAnalysisItem success(
            String workflowPath,
            AnalysisResponse analysis,
            WorkflowMetricsResult workflowMetrics) {
        return new WorkflowAnalysisItem(workflowPath, analysis, workflowMetrics, null);
    }

    public static WorkflowAnalysisItem failure(String workflowPath, String errorMessage) {
        return new WorkflowAnalysisItem(workflowPath, null, null, errorMessage);
    }

    public boolean succeeded() {
        return analysis != null;
    }
}