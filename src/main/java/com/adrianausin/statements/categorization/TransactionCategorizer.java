package com.adrianausin.statements.categorization;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import com.adrianausin.statements.categorization.CategorizationProperties.Direction;
import org.springframework.stereotype.Component;

@Component
public class TransactionCategorizer {

    private final List<CompiledRule> rules;

    public TransactionCategorizer(CategorizationProperties properties) {
        this.rules = properties.rules().stream()
                .map(rule -> new CompiledRule(rule.category(),
                        Pattern.compile(rule.pattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                        rule.direction()))
                .toList();
    }

    public Category categorize(String description, BigDecimal amount) {
        return rules.stream()
                .filter(rule -> rule.matches(description, amount))
                .map(CompiledRule::category)
                .findFirst()
                .orElse(Category.OTHER);
    }

    private record CompiledRule(Category category, Pattern pattern, Direction direction) {

        boolean matches(String description, BigDecimal amount) {
            boolean directionMatches = switch (direction) {
                case IN -> amount.signum() > 0;
                case OUT -> amount.signum() < 0;
                case ANY -> true;
            };
            return directionMatches && pattern.matcher(description).find();
        }
    }
}
