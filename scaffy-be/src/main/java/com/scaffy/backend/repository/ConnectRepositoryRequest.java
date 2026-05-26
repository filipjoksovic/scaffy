package com.scaffy.backend.repository;

import jakarta.validation.constraints.NotBlank;

public record ConnectRepositoryRequest(@NotBlank String repository) {
}
