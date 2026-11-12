/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

/** Line range (inclusive, 0-based) of a table found in a document. */
public record TableRange(int startLine, int endLine) {}
