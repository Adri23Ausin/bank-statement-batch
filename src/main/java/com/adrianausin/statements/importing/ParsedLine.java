package com.adrianausin.statements.importing;

import com.adrianausin.statements.parsing.StatementLine;

/**
 * A statement line together with where it came from, for error reporting.
 */
public record ParsedLine(int lineNumber, String raw, StatementLine line) {
}
