package org.libprunus.core.log.runtime;

/**
 * Non-sealed extension of {@link TypeRenderer} to permit lambda / method-reference instances.
 * Direct dispatch table entries use this interface so the package-internal lambda forms
 * (such as {@code ENUM_RENDERER}, {@code CHAR_SEQUENCE_RENDERER}, inline numeric/whitelist
 * renderers, and {@code INLINE_EXACT_RENDERERS_CANDIDATE} entries) can be expressed as lambdas
 * while {@link TypeRenderer} itself remains sealed against external extension.
 */
non-sealed interface NonSealedTypeRenderer extends TypeRenderer {}
