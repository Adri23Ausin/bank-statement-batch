package com.adrianausin.statements.importing;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param directory      where uploaded and dropped statements are stored, one sub-directory per import
 * @param pollingEnabled whether CSV files dropped into {@code directory} are imported automatically
 */
@ConfigurationProperties("statements.inbox")
public record InboxProperties(
        @DefaultValue("inbox") Path directory,
        @DefaultValue("false") boolean pollingEnabled,
        @DefaultValue("1m") Duration pollInterval) {
}
