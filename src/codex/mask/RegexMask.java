package codex.mask;

import java.util.regex.Pattern;

/**
 * Маска проверки строковых свойств на соответствие регулярному выражению.
 */
public class RegexMask implements IMask<String> {
    
    private final Pattern pattern;
    
    /**
     * Конструктор маски.
     * @param expression Выражение, которому должно соответствовать сначение
     * свойства.
     */
    public RegexMask(String expression) {
        this(Pattern.compile(expression));
    }
    
    /**
     * Конструктор маски.
     * @pattern expression Паттерн, которому должно соответствовать сначение
     * свойства.
     */
    public RegexMask(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean verify(String value) {
        return this.pattern.matcher(value).matches();
    }
    
}
