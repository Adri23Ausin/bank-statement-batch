package com.adrianausin.statements.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.adrianausin.statements.importing.ImportLauncher;
import com.adrianausin.statements.importing.ImportStatusService;
import com.adrianausin.statements.importing.ImportStatusService.ImportStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Imports", description = "Upload bank statements and follow their import")
class ImportController {

    private final ImportLauncher launcher;
    private final ImportStatusService statuses;

    ImportController(ImportLauncher launcher, ImportStatusService statuses) {
        this.launcher = launcher;
        this.statuses = statuses;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one or more statement files (named <IBAN>_<anything>.csv) and import them "
            + "in the background")
    ResponseEntity<Map<String, String>> upload(@RequestPart("files") List<MultipartFile> files) throws IOException {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }
        Map<String, InputStream> contents = new LinkedHashMap<>();
        for (MultipartFile file : files) {
            contents.put(file.getOriginalFilename(), file.getInputStream());
        }
        String importId = launcher.submit(contents);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/imports/" + importId))
                .body(Map.of("importId", importId));
    }

    @GetMapping("/{importId}")
    @Operation(summary = "Progress and result of an import: per-file counts and skipped lines")
    ResponseEntity<ImportStatus> status(@PathVariable String importId) {
        return ResponseEntity.of(statuses.status(importId));
    }
}
