package com.adrianausin.statements.parsing;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 field splitter: supports quoted fields containing the delimiter and
 * escaped quotes ({@code ""}). Enough for bank exports without pulling in a CSV library.
 */
final class CsvFields {

    private CsvFields() {
    }

    static List<String> split(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                fields.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new InvalidStatementLineException("Unterminated quoted field");
        }
        fields.add(current.toString().strip());
        return fields;
    }
}
