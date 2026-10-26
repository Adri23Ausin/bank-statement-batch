package com.adrianausin.statements.parsing;

/**
 * A line that can't be turned into a {@link StatementLine}. The import step skips these lines
 * and records them instead of failing the whole file.
 */
public class InvalidStatementLineException extends RuntimeException {

    public InvalidStatementLineException(String message) {
        super(message);
    }

    public InvalidStatementLineException(String message, Throwable cause) {
        super(message, cause);
    }
}
