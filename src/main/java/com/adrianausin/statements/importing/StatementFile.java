package com.adrianausin.statements.importing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.adrianausin.statements.parsing.StatementFormat;
import com.adrianausin.statements.parsing.StatementFormats;

/**
 * A statement file named {@code <accountId>_<anything>.csv}, e.g.
 * {@code ES9121000418450200051332_2026-09.csv}. The bank format is detected from the header.
 */
public record StatementFile(Path path, String accountId, StatementFormat format) {

    private static final Pattern FILE_NAME = Pattern.compile("([A-Z]{2}[0-9]{2}[A-Z0-9]{1,30})_.*\\.csv",
            Pattern.CASE_INSENSITIVE);

    public static StatementFile open(Path path) {
        String fileName = path.getFileName().toString();
        Matcher matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "File name must look like <IBAN>_<anything>.csv, got " + fileName);
        }
        String header = readHeader(path);
        StatementFormat format = StatementFormats.detect(header)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown statement format in %s, header was: %s".formatted(fileName, header)));
        return new StatementFile(path, matcher.group(1).toUpperCase(), format);
    }

    public String name() {
        return path.getFileName().toString();
    }

    private static String readHeader(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            return header == null ? "" : header;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
