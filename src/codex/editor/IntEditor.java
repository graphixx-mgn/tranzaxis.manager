package codex.editor;

import static codex.editor.IEditor.BORDER_ACTIVE;
import static codex.editor.IEditor.COLOR_DISABLED;
import static codex.editor.IEditor.COLOR_NORMAL;
import static codex.editor.IEditor.FONT_VALUE;
import codex.property.PropertyHolder;
import codex.type.Int;
import codex.utils.ImageUtils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Редактор свойств типа {@link Int}, представляет собой поле ввода.
 */
public class IntEditor extends AbstractEditor implements DocumentListener {
    
    private JTextField textField;
    private String     initialValue;
    private String     previousValue;

    private Predicate<String>        checker;
    private final Consumer<String>   update;
    private final Consumer<String>   commit;
    private Function<String, Object> transformer;
    
    private final JLabel signDelete;
    
    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public IntEditor(PropertyHolder propHolder) {
        this(
                propHolder,
                (text) -> {
                    try {
                        return text.isEmpty() || Integer.valueOf(text) <= Integer.MAX_VALUE;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                Integer::valueOf
        );
    }

    /**
     * Внутренний конструктор редактора, вызывается публичным конструктором.
     * @param propHolder Редактируемое свойство.
     * @param checker Функция-предикат для непрерывной проверки ввода.
     * @param transformer Функция-конвертер, вызывается для получения значения 
     * свойства из введенного текста при фиксации изменения.
     */
    private IntEditor(PropertyHolder propHolder, Predicate<String> checker, Function<String, Object> transformer) {
        super(propHolder);
        this.checker     = checker;
        this.transformer = transformer;
        
        signDelete = new JLabel(ImageUtils.resize(
                ImageUtils.getByPath("/images/clearval.png"), 
                textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
        ));
        signDelete.setBorder(new EmptyBorder(0, 3, 0, 0));
        signDelete.setCursor(Cursor.getDefaultCursor());
        
        this.update = (text) -> {
            setBorder(BORDER_ACTIVE);
            textField.setForeground(COLOR_NORMAL);
            if (checker.test(text)) {
                previousValue = text;
            } else {
                setValue(previousValue);
            }
        };
        this.commit = (text) -> {
            if (!text.equals(initialValue)) {
                propHolder.setValue(
                        previousValue == null || previousValue.isEmpty() ? null : transformer.apply(previousValue)
                );
                initialValue = previousValue;
            }
        };
        
        textField.getDocument().addDocumentListener(this);
//        textField.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyTyped(KeyEvent event) {
//                if (event.getKeyChar() == KeyEvent.VK_TAB) {
//                    stopEditing();
//                }
//                if (event.getKeyChar() == KeyEvent.VK_ENTER) {
//                    stopEditing();
//                    KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
//                }
//            }
//        });
        
        PlaceHolder placeHolder = new PlaceHolder(IEditor.NOT_DEFINED, textField, PlaceHolder.Show.FOCUS_LOST);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(signDelete, BorderLayout.EAST);
        
        signDelete.setVisible(!propHolder.isEmpty() && textField.isFocusOwner());
        textField.add(controls, BorderLayout.EAST);
        
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setValue(null);
                propHolder.setValue(null);
            }
        });
    }
    
    @Override
    public Box createEditor() {
        textField = new JTextField();
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.addFocusListener(this);

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
        textField.setFocusable(editable);
    }

    @Override
    public void setValue(Object value) {
        SwingUtilities.invokeLater(() -> {
            textField.getDocument().removeDocumentListener(this);
            initialValue = value == null ? "" : value.toString();
            textField.setText(initialValue);
            textField.getDocument().addDocumentListener(this);
            signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        });
    }
    
    @Override
    public boolean stopEditing() {
        commit.accept(textField.getText());
        return true;
    }

    @Override
    public void focusGained(FocusEvent event) {
        super.focusGained(event);
        signDelete.setVisible(!propHolder.isEmpty() && isEditable());
        initialValue  = textField.getText();
        previousValue = initialValue;
    }
    
    @Override
    public void focusLost(FocusEvent event) {
        super.focusLost(event);
        signDelete.setVisible(false);
        stopEditing();
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата 
     * при вставке текста
     */
    @Override
    public void insertUpdate(DocumentEvent event) {
        signDelete.setVisible(!textField.getText().isEmpty() && isEditable());
        update.accept(textField.getText());
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата
     * при удалении фрагмента текста.
     */
    @Override
    public void removeUpdate(DocumentEvent event) {
        signDelete.setVisible(!textField.getText().isEmpty() && isEditable());
        update.accept(textField.getText());
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата
     * при редактировании текста.
     */
    @Override
    public void changedUpdate(DocumentEvent event) {
        signDelete.setVisible(!textField.getText().isEmpty() && isEditable());
        update.accept(textField.getText());
    }

    @Override
    public Component getFocusTarget() {
        return textField;
    }

}
