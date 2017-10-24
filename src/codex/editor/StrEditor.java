package codex.editor;

import codex.property.PropertyHolder;
import codex.type.Str;
import java.awt.event.FocusEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Редактор свойств типа {@link Str}, представляет собой поле ввода.
 */
public class StrEditor extends AbstractEditor  implements DocumentListener {
    
    protected JTextField textField;
    protected String     initialValue;
    protected String     previousValue;
    
    protected Predicate<String>        checker;
    protected Function<String, Object> transformer;
    protected final Consumer<String>   commit = (text) -> {
        if (!text.equals(initialValue)) {
            propHolder.setValue(
                    previousValue == null || previousValue.isEmpty() ? null : transformer.apply(previousValue)
            );
        }
    };
    
    protected final Consumer<String>   update = (text) -> {
        if (checker.test(text)) {
            previousValue = text;
        } else {
            setValue(previousValue);
        }
    };

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public StrEditor(PropertyHolder propHolder) {
        this(propHolder, (text) -> {
            return true;
        }, String::valueOf);
    }
    
    /**
     * Внутренний конструктор редактора, вызывается публичным конструктором.
     * @param propHolder Редактируемое свойство.
     * @param checker Функция-предикат для непрерывной проверки ввода.
     * @param transformer Функция-конвертер, вызывается для получения значения 
     * свойства из введенного текста при фиксации изменения.
     */
    protected StrEditor(PropertyHolder propHolder, Predicate<String> checker, Function<String, Object> transformer) {
        super(propHolder);
        this.checker     = checker;
        this.transformer = transformer;
        textField.getDocument().addDocumentListener(this);
    }

    @Override
    public Box createEditor() {
        textField = new JTextField();        
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.addFocusListener(this);
        
        PlaceHolder placeHolder = new PlaceHolder(IEditor.NOT_DEFINED, textField, PlaceHolder.Show.FOCUS_LOST);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);

        Box container = new Box(BoxLayout.X_AXIS);
        container.setBackground(textField.getBackground());
        container.add(textField);
        return container;
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        textField.setForeground(editable && !propHolder.isInherited() ? COLOR_NORMAL : COLOR_DISABLED);
        textField.setEditable(editable && !propHolder.isInherited());
    }

    @Override
    public void setValue(Object value) {
        SwingUtilities.invokeLater(() -> {
            textField.getDocument().removeDocumentListener(this);
            textField.setText(value == null ? "" : value.toString());
            textField.getDocument().addDocumentListener(this);
        });
    }

    @Override
    public void focusGained(FocusEvent event) {
        super.focusGained(event);
        initialValue = textField.getText();
    }
    
    @Override
    public void focusLost(FocusEvent event) {
        super.focusLost(event);
        commit.accept(textField.getText());
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата 
     * при вставке текста
     */
    @Override
    public void insertUpdate(DocumentEvent event) {
        update.accept(textField.getText());
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата
     * при удалении фрагмента текста.
     */
    @Override
    public void removeUpdate(DocumentEvent event) {
        update.accept(textField.getText());
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата
     * при редактировании текста.
     */
    @Override
    public void changedUpdate(DocumentEvent event) {
        update.accept(textField.getText());
    }

}
