package com.scaffy.backend.init.generator;

/**
 * One file destined for the generated ZIP. Both the template-overlay path
 * and the cached-artifact path produce these; ZipBuilder just writes them.
 */
public record EmittedFile(String destinationPath, byte[] content) {
}
