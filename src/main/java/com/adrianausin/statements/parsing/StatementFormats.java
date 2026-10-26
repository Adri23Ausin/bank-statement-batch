package com.adrianausin.statements.parsing;

import java.util.List;
import java.util.Optional;

/**
 * Registry of the supported bank formats.
 */
public final class StatementFormats {

    private static final List<StatementFormat> ALL = List.of(
            new StatementFormat.IberBank(),
            new StatementFormat.NorthBank());

    private StatementFormats() {
    }

    /**
     * Finds the format of a file from its header line.
     */
    public static Optional<StatementFormat> detect(String headerLine) {
        String header = headerLine.startsWith("﻿") ? headerLine.substring(1) : headerLine;
        return ALL.stream().filter(format -> format.supports(header)).findFirst();
    }

    public static StatementFormat byName(String name) {
        return ALL.stream()
                .filter(format -> format.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown statement format " + name));
    }
}
