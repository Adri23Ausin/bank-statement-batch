package com.adrianausin.statements;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StatementBatchApplicationIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayCreatesTheSpringBatchSchema() {
        Long tables = jdbc.sql("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_name IN ('batch_job_instance', 'batch_job_execution', 'batch_step_execution')
                        """)
                .query(Long.class).single();

        assertThat(tables).isEqualTo(3);
    }
}
