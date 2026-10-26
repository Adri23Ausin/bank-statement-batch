package com.adrianausin.statements.importing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * One partition per statement file in the input directory, so files are imported in parallel
 * and each one can fail, be skipped or restart independently.
 */
public class StatementFilesPartitioner implements Partitioner {

    public static final String INPUT_FILE = "inputFile";

    private final Path directory;

    public StatementFilesPartitioner(Path directory) {
        this.directory = directory;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
        List<Path> files = statementFiles();
        for (int i = 0; i < files.size(); i++) {
            ExecutionContext context = new ExecutionContext();
            context.putString(INPUT_FILE, files.get(i).toAbsolutePath().toString());
            partitions.put("file" + i, context);
        }
        return partitions;
    }

    private List<Path> statementFiles() {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list statement files in " + directory, e);
        }
    }
}
