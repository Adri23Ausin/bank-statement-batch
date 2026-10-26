package com.adrianausin.statements.categorization;

import java.math.BigDecimal;
import java.util.List;

import com.adrianausin.statements.categorization.CategorizationProperties.Direction;
import com.adrianausin.statements.categorization.CategorizationProperties.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCategorizerTest {

    @Test
    void firstMatchingRuleWins() {
        TransactionCategorizer categorizer = new TransactionCategorizer(new CategorizationProperties(List.of(
                new Rule(Category.INSURANCE, "seguro", Direction.ANY),
                new Rule(Category.TRANSPORT, "coche", Direction.ANY))));

        assertThat(categorizer.categorize("SEGURO COCHE", new BigDecimal("-10"))).isEqualTo(Category.INSURANCE);
    }

    @Test
    void rulesCanBeRestrictedToMoneyInOrOut() {
        TransactionCategorizer categorizer = new TransactionCategorizer(new CategorizationProperties(List.of(
                new Rule(Category.INCOME, "salary", Direction.IN))));

        assertThat(categorizer.categorize("SALARY", new BigDecimal("2000"))).isEqualTo(Category.INCOME);
        assertThat(categorizer.categorize("SALARY ADVANCE REPAYMENT", new BigDecimal("-200")))
                .isEqualTo(Category.OTHER);
    }

    /**
     * Checks the rules shipped in application.yml against real-looking descriptions.
     */
    @ParameterizedTest
    @CsvSource({
            "NOMINA SEPTIEMBRE ACME TECH SL,          2350,    INCOME",
            "SALARY ACME LTD,                          3150,    INCOME",
            "ALQUILER PISO SEPTIEMBRE REF ALQ-2026-09, -950,   HOUSING",
            "RENT SEPTEMBER REF RENT-0926,             -1450,   HOUSING",
            "RECIBO IBERDROLA CLIENTES REF IBD-778812, -78.45, UTILITIES",
            "SEGURO COCHE MAPFRE REF MAP-99231,        -412.18, INSURANCE",
            "COUNCIL TAX REF CT-2026-09,               -142,    TAXES",
            "NETFLIX.COM,                              -13.99,  SUBSCRIPTIONS",
            "MERCADONA AVDA AMERICA,                   -64.37,  GROCERIES",
            "'SAINSBURY''S, CAMDEN',                   -38.27,  GROCERIES",
            "CAFETERIA EL RINCON,                      -3.20,   RESTAURANTS",
            "THE BLUE POSTS PUB,                       -18.40,  RESTAURANTS",
            "TFL TRAVEL CH,                            -2.80,   TRANSPORT",
            "REPSOL E.S. 4521,                         -55,     TRANSPORT",
            "FARMACIA LDA. MARTIN,                     -12.40,  HEALTH",
            "CAJERO ATM 0182,                          -100,    CASH",
            "TRANSFERENCIA A AHORRO,                   -200,    SAVINGS",
            "AMAZON EU SARL,                           -32.99,  SHOPPING",
            "BIZUM DE LUCIA CENA,                      18.50,   OTHER"
    })
    void shippedRulesCategoriseTheSampleStatements(String description, BigDecimal amount, Category expected) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RulesFromApplicationYml.class)
                .run(context -> assertThat(context.getBean(TransactionCategorizer.class)
                        .categorize(description, amount)).isEqualTo(expected));
    }

    @EnableConfigurationProperties(CategorizationProperties.class)
    static class RulesFromApplicationYml {

        @Bean
        TransactionCategorizer categorizer(CategorizationProperties properties) {
            return new TransactionCategorizer(properties);
        }
    }
}
