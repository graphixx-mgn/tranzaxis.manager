package codex.mask;

import java.util.regex.Pattern;

/**
 * Маска проверки строковых свойств на соответствие регулярному выражению.
 */
public class RegexMask implements IMask<String> {
    
    private final Pattern pattern;
    private final String  errorHint;
    
    /**
     * Конструктор маски.
     * @param expression Выражение, которому должно соответствовать сначение
     * свойства.
     */
    public RegexMask(String expression, String  errorHint) {
        this(Pattern.compile(expression), errorHint);
    }
    
    /**
     * Конструктор маски.
     * @pattern expression Паттерн, которому должно соответствовать сначение
     * свойства.
     */
    public RegexMask(Pattern pattern, String errorHint) {
        this.pattern   = pattern;
        this.errorHint = errorHint;
    }

    @Override
    public boolean verify(String value) {
        return this.pattern.matcher(value).matches();
    }
    
    @Override
    public String getErrorHint() {
        return errorHint;
    };
    
}
