package codex.editor;

import codex.property.PropertyHolder;
import codex.type.BigInt;
import codex.type.Int;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Редактор свойств типа {@link Int}, представляет собой поле ввода.
 */
@ThreadSafe
public class BigIntEditor extends AbstractEditor<BigInt, Long> implements DocumentListener {

    private JTextField textField;
    private String     previousValue;

    private final Consumer<String> update;
    private final Consumer<String> commit;

    private final JLabel signDelete;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public BigIntEditor(PropertyHolder<BigInt, Long> propHolder) {
        this(
                propHolder,
                (text) -> {
                    try {
                        return text.isEmpty() || Long.valueOf(text) <= Long.MAX_VALUE;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                text -> text.isEmpty() ? 0L : Long.valueOf(text)
        );
    }

    /**
     * Внутренний конструктор редактора, вызывается публичным конструктором.
     * @param propHolder Редактируемое свойство.
     * @param checker Функция-предикат для непрерывной проверки ввода.
     * @param transformer Функция-конвертер, вызывается для получения значения
     * свойства из введенного текста при фиксации изменения.
     */
    private BigIntEditor(PropertyHolder<BigInt, Long> propHolder, Predicate<String> checker, Function<String, Long> transformer) {
        super(propHolder);
        
        signDelete = new JLabel(ImageUtils.resize(
                ImageUtils.getByPath("/images/clearval.png"), 
                textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
        ));
        signDelete.setBorder(new EmptyBorder(0, 3, 0, 0));
        signDelete.setCursor(Cursor.getDefaultCursor());
        
        this.update = (text) -> {
            if (getBorder() == BORDER_ERROR) {
                setBorder(BORDER_ACTIVE);
            }
            textField.setForeground(COLOR_NORMAL);
            if (checker.test(text)) {
                previousValue = text;
            } else {
                setValue(transformer.apply(previousValue));
            }
        };
        this.commit = (text) -> {
            if (!transformer.apply(text).equals(propHolder.getPropValue().getValue())) {
                propHolder.setValue(
                        text == null || text.isEmpty() ? null : transformer.apply(text)
                );
            }
        };
        
        textField.getDocument().addDocumentListener(this);
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                char c = event.getKeyChar();
                if (!((c >= '0') && (c <= '9') ||
                     (c == KeyEvent.VK_BACK_SPACE) ||
                     (c == KeyEvent.VK_DELETE))) 
                {
                    event.consume();
                }
                if (event.getKeyChar() == KeyEvent.VK_TAB) {
                    stopEditing();
                }
                if (event.getKeyChar() == KeyEvent.VK_ENTER) {
                    stopEditing();
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                }
            }

            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_UP) {
                    int curr = Integer.valueOf(previousValue != null && !previousValue.isEmpty() ? previousValue : "0")+1;
                    if (curr < Integer.MAX_VALUE) {
                        update.accept(String.valueOf(curr));
                        textField.setText(String.valueOf(curr));
                    }
                }
                if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                    int curr = Integer.valueOf(previousValue != null && !previousValue.isEmpty() ? previousValue : "0")-1;
                    if (curr >= 0) {
                        update.accept(String.valueOf(curr));
                        textField.setText(String.valueOf(curr));
                    }
                }
            }
            
        });
        
        PlaceHolder placeHolder = new PlaceHolder(IEditor.NOT_DEFINED, textField, PlaceHolder.Show.FOCUS_LOST);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(signDelete, BorderLayout.EAST);
        
        signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        textField.add(controls, BorderLayout.EAST);
        
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                previousValue = null;
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
        SwingUtilities.invokeLater(() -> {
            textField.setForeground(editable && !propHolder.isInherited() ? COLOR_NORMAL : COLOR_DISABLED);
            textField.setEditable(editable && !propHolder.isInherited());
            textField.setFocusable(editable);
        });
    }

    @Override
    public void setValue(Long value) {
        SwingUtilities.invokeLater(() -> {
            textField.getDocument().removeDocumentListener(this);
            textField.setText(value == null ? "" : String.valueOf(value));
            textField.getDocument().addDocumentListener(this);
            if (signDelete!= null) {
                signDelete.setVisible(!textField.getText().isEmpty() && isEditable() && textField.isFocusOwner());
            }
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
        previousValue = textField.getText();
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
