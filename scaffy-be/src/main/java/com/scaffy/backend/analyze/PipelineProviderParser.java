package com.scaffy.backend.analyze;

import java.util.Map;

public interface PipelineProviderParser {

	PipelineProvider provider();

	PipelineDocument parse(Map<?, ?> root);
}
