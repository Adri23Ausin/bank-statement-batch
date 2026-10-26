package com.adrianausin.statements.reporting;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("statements.reports")
public record ReportProperties(@DefaultValue("reports") Path directory) {
}
