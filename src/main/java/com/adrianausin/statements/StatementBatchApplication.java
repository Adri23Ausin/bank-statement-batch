package com.adrianausin.statements;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StatementBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatementBatchApplication.class, args);
    }
}
