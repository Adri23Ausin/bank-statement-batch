package com.adrianausin.statements.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementFormatTest {

    @Test
    void detectsTheFormatFromTheHeader() {
        assertThat(StatementFormats.detect("Fecha;Concepto;Importe;Divisa;Saldo"))
                .containsInstanceOf(StatementFormat.IberBank.class);
        assertThat(StatementFormats.detect("Date,Description,Debit,Credit,Currency"))
                .containsInstanceOf(StatementFormat.NorthBank.class);
        assertThat(StatementFormats.detect("﻿Date,Description,Debit,Credit,Currency"))
                .as("UTF-8 byte order mark written by Excel")
                .containsInstanceOf(StatementFormat.NorthBank.class);
        assertThat(StatementFormats.detect("id,amount")).isEmpty();
    }

    @Nested
    class IberBank {

        private final StatementFormat format = new StatementFormat.IberBank();

        @Test
        void parsesSpanishNumbersAndDates() {
            StatementLine line = format.parse("28/09/2026;RECIBO IBERDROLA CLIENTES;-1.234,56;EUR;2.100,00");

            assertThat(line).isEqualTo(new StatementLine(LocalDate.of(2026, 9, 28), "RECIBO IBERDROLA CLIENTES",
                    new BigDecimal("-1234.56"), "EUR"));
        }

        @Test
        void supportsQuotedDescriptions() {
            StatementLine line = format.parse("01/09/2026;\"BIZUM DE \"\"ANA\"\"; CENA\";25,00;EUR;100,00");

            assertThat(line.description()).isEqualTo("BIZUM DE \"ANA\"; CENA");
            assertThat(line.amount()).isEqualByComparingTo("25");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "2026-09-28;COMPRA;-10,00;EUR;0,00",
                "31/09/2026;FECHA IMPOSIBLE;-10,00;EUR;0,00",
                "28/09/2026;COMPRA;diez;EUR;0,00",
                "28/09/2026;;-10,00;EUR;0,00",
                "28/09/2026;COMPRA;-10,00;EUR",
                "28/09/2026;COMPRA;0,00;EUR;0,00",
                "28/09/2026;COMPRA;-10,00;XYZ;0,00"
        })
        void rejectsInvalidLines(String line) {
            assertThatThrownBy(() -> format.parse(line)).isInstanceOf(InvalidStatementLineException.class);
        }
    }

    @Nested
    class NorthBank {

        private final StatementFormat format = new StatementFormat.NorthBank();

        @Test
        void debitsBecomeNegativeAmounts() {
            assertThat(format.parse("2026-09-03,TESCO STORES 2041,42.10,,GBP").amount())
                    .isEqualByComparingTo("-42.10");
        }

        @Test
        void creditsBecomePositiveAmounts() {
            assertThat(format.parse("2026-09-25,SALARY ACME LTD,,3150.00,GBP"))
                    .isEqualTo(new StatementLine(LocalDate.of(2026, 9, 25), "SALARY ACME LTD",
                            new BigDecimal("3150.00"), "GBP"));
        }

        @Test
        void normalisesWhitespaceInDescriptions() {
            assertThat(format.parse("2026-09-03,\"TFL   TRAVEL  CH\",2.80,,gbp").description())
                    .isEqualTo("TFL TRAVEL CH");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "2026-09-03,BOTH,1.00,2.00,GBP",
                "2026-09-03,NEITHER,,,GBP",
                "03/09/2026,WRONG DATE,1.00,,GBP",
                "2026-09-03,\"UNTERMINATED,1.00,,GBP"
        })
        void rejectsInvalidLines(String line) {
            assertThatThrownBy(() -> format.parse(line)).isInstanceOf(InvalidStatementLineException.class);
        }
    }
}
