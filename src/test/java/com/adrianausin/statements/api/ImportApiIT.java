package com.adrianausin.statements.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import com.adrianausin.statements.TestcontainersConfiguration;
import com.adrianausin.statements.importing.ImportLauncher;
import com.adrianausin.statements.importing.InboxProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportApiIT {

    private static final String SPANISH = "ES9121000418450200051332_2026-09.csv";
    private static final String BRITISH = "GB33BUKB20201555555555_2026-09.csv";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ImportLauncher launcher;

    @Autowired
    InboxProperties inbox;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("TRUNCATE expected_payments, bank_transactions, import_errors").update();
    }

    @Test
    void uploadsStatementsAndReportsTheImportResult() throws Exception {
        MvcTestResult upload = mvc.post().uri("/api/v1/imports").multipart()
                .file(sample(SPANISH))
                .file(sample(BRITISH))
                .exchange();

        assertThat(upload).hasStatus(HttpStatus.ACCEPTED).containsHeader("Location");
        String importId = JsonPath.read(upload.getResponse().getContentAsString(), "$.importId");

        String status = awaitCompletion(importId);

        assertThat(JsonPath.<String>read(status, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<Integer>read(status, "$.files.length()")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(status, "$.files[0].written")).isEqualTo(25);
        assertThat(JsonPath.<Integer>read(status, "$.files[0].skipped")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(status, "$.errors.length()")).isEqualTo(4);
        assertThat(JsonPath.<String>read(status, "$.errors[0].reason")).isEqualTo("Invalid amount ''");
    }

    @Test
    void rejectsFilesWithAnUnknownFormat() {
        MockMultipartFile unknown = new MockMultipartFile("files", "ES9121000418450200051332_x.csv",
                MediaType.TEXT_PLAIN_VALUE, "id,amount\n1,2\n".getBytes());

        assertThat(mvc.post().uri("/api/v1/imports").multipart().file(unknown))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().startsWith("Unknown statement format");
    }

    @Test
    void rejectsFilesThatAreNotCsv() {
        MockMultipartFile pdf = new MockMultipartFile("files", "statement.pdf", "application/pdf", new byte[] {1});

        assertThat(mvc.post().uri("/api/v1/imports").multipart().file(pdf)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importsFilesDroppedInTheInbox() throws Exception {
        Files.createDirectories(inbox.directory());
        Files.copy(Path.of("samples", BRITISH), inbox.directory().resolve(BRITISH));

        String importId = launcher.importDroppedFiles();

        assertThat(inbox.directory().resolve(BRITISH)).doesNotExist();
        assertThat(launcher.importDirectory(importId).resolve(BRITISH)).exists();
        assertThat(jdbc.sql("SELECT count(*) FROM bank_transactions").query(Long.class).single()).isEqualTo(22);
        assertThat(launcher.importDroppedFiles()).as("nothing left to import").isNull();
    }

    @Test
    void unknownImportsAreNotFound() {
        assertThat(mvc.get().uri("/api/v1/imports/does-not-exist")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void exposesTransactionsReportsAndReconciliation() throws Exception {
        assertThat(mvc.post().uri("/api/v1/expected-payments").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId": "ES9121000418450200051332", "reference": "MAP-99231", "description": "Car insurance",
                 "amount": -412.18, "dueDate": "2026-09-30"}
                """)).hasStatus(HttpStatus.CREATED);
        String importId = JsonPath.read(mvc.post().uri("/api/v1/imports").multipart().file(sample(SPANISH)).exchange()
                .getResponse().getContentAsString(), "$.importId");
        awaitCompletion(importId);

        assertThat(mvc.get().uri("/api/v1/accounts/ES9121000418450200051332/reports/2026-09"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.income").isEqualTo(2401.49);
        assertThat(mvc.get().uri("/api/v1/accounts/ES9121000418450200051332/transactions?month=2026-09&category=GROCERIES"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(4);
        assertThat(mvc.get().uri("/api/v1/expected-payments?accountId=ES9121000418450200051332"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].status").isEqualTo("MATCHED");
        assertThat(mvc.get().uri("/api/v1/accounts/ES9121000418450200051332/reports/2026-08"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private String awaitCompletion(String importId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            String body = mvc.get().uri("/api/v1/imports/" + importId).exchange().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (!status.equals("QUEUED") && !status.equals("STARTING") && !status.equals("STARTED")) {
                return body;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Import " + importId + " did not finish in time");
    }

    private static MockMultipartFile sample(String name) throws Exception {
        return new MockMultipartFile("files", name, "text/csv", Files.readAllBytes(Path.of("samples", name)));
    }
}
