package com.adrianausin.statements.categorization;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Categorisation rules, evaluated in order: the first rule whose pattern matches the
 * description (and whose direction matches the amount sign) wins.
 */
@Validated
@ConfigurationProperties("statements.categorization")
public record CategorizationProperties(@Valid List<Rule> rules) {

    public CategorizationProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * @param pattern   case-insensitive regular expression searched in the description
     * @param direction whether the rule applies to money in, money out or both
     */
    public record Rule(@NotNull Category category, @NotBlank String pattern, Direction direction) {

        public Rule {
            direction = direction == null ? Direction.ANY : direction;
        }
    }

    public enum Direction {
        IN, OUT, ANY
    }
}
