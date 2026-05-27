package com.scaffy.backend.init;

import java.util.UUID;

public class InitJobNotFoundException extends RuntimeException {

	public InitJobNotFoundException(UUID id) {
		super("Initializer job '" + id + "' was not found.");
	}
}
