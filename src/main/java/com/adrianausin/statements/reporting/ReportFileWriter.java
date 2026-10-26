package com.adrianausin.statements.reporting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.adrianausin.statements.reporting.MonthlyReport.CategoryTotal;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code <directory>/<accountId>/<yyyy-MM>.json} and {@code .csv}. Files are written to a
 * temporary name and then moved, so readers never see a half-written report.
 */
@Component
public class ReportFileWriter {

    private final JsonMapper jsonMapper;
    private final ReportProperties properties;

    public ReportFileWriter(JsonMapper jsonMapper, ReportProperties properties) {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    public List<Path> write(MonthlyReport report) {
        Path directory = properties.directory().resolve(report.accountId());
        String baseName = report.month().toString();
        Path json = directory.resolve(baseName + ".json");
        Path csv = directory.resolve(baseName + ".csv");
        writeAtomically(json, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        writeAtomically(csv, toCsv(report));
        return List.of(json, csv);
    }

    private static String toCsv(MonthlyReport report) {
        StringBuilder csv = new StringBuilder("category,transactions,total,currency\n");
        for (CategoryTotal category : report.categories()) {
            csv.append(category.category()).append(',')
                    .append(category.transactions()).append(',')
                    .append(category.total().toPlainString()).append(',')
                    .append(report.currency()).append('\n');
        }
        return csv.toString();
    }

    private static void writeAtomically(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write report " + target, e);
        }
    }
}
