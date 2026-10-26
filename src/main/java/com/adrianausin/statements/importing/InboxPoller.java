package com.adrianausin.statements.importing;

import java.io.IOException;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Imports the CSV files dropped into the inbox directory (e.g. by an SFTP job), so statements
 * can be imported without calling the API.
 */
@Component
@EnableScheduling
@ConditionalOnBooleanProperty("statements.inbox.polling-enabled")
class InboxPoller {

    private static final Logger log = LoggerFactory.getLogger(InboxPoller.class);

    private final ImportLauncher launcher;
    private final InboxProperties inbox;

    InboxPoller(ImportLauncher launcher, InboxProperties inbox) throws IOException {
        this.launcher = launcher;
        this.inbox = inbox;
        Files.createDirectories(inbox.directory());
    }

    @Scheduled(fixedDelayString = "${statements.inbox.poll-interval}")
    void poll() {
        try {
            String importId = launcher.importDroppedFiles();
            if (importId != null) {
                log.info("Imported files dropped in {} as import {}", inbox.directory(), importId);
            }
        } catch (IOException e) {
            log.error("Cannot read the inbox directory {}", inbox.directory(), e);
        }
    }
}
