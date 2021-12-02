package codex.editor;

import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.TimingUtils;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Редактор свойств типа {@link Str}, представляет собой поле ввода.
 */
@ThreadSafe
public class StrEditor extends AbstractEditor<Str, String> implements DocumentListener {
    
    private JTextField  textField;
    private PlaceHolder placeHolder;
    
    private final Consumer<String> update;
    private final Consumer<String> commit;
    
    private final JLabel signInvalid;
    private final JLabel signDelete;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public StrEditor(PropertyHolder<Str, String> propHolder) {
        this(
                propHolder,
                String::valueOf
        );
    }
    
    /**
     * Внутренний конструктор редактора, вызывается публичным конструктором.
     * @param propHolder Редактируемое свойство.
     * @param transformer Функция-конвертер, вызывается для получения значения 
     * свойства из введенного текста при фиксации изменения.
     */
    private StrEditor(PropertyHolder<Str, String> propHolder, Function<String, String> transformer) {
        super(propHolder);

        placeHolder = new PlaceHolder(propHolder.getPlaceholder(), textField);
        placeHolder.setBorder(textField.getBorder());
        placeHolder.changeAlpha(100);
        placeHolder.setVisible(textField.getText().isEmpty());

        int height = textField.getPreferredSize().height;
        signInvalid = new JLabel(ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), height, height));
        signDelete = new JLabel(ImageUtils.resize(
                ImageUtils.getByPath("/images/clearval.png"), 
                textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
        ));
        
        signInvalid.setBorder(new EmptyBorder(0, 3, 0, 0));
        signInvalid.setCursor(Cursor.getDefaultCursor());
        signDelete.setCursor(Cursor.getDefaultCursor());

        IMask<String> mask = IComplexType.coalesce(propHolder.getPropValue().getMask(), (String text) -> true);
        if (mask.getErrorHint() != null) {
            signInvalid.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    TimingUtils.showTimedBalloon(getErrorTip(), 3000);
                }
            });
        }
        
        this.update = (text) -> {
            if (getBorder() == BORDER_ERROR) {
                setBorder(BORDER_ACTIVE);
            }
            signInvalid.setVisible(false);
            textField.setForeground(COLOR_NORMAL);
        };
        this.commit = (text) -> {
            if (!text.equals(propHolder.getPropValue().getValue())) {
                propHolder.setValue(
                        text.isEmpty() ? null : transformer.apply(text)
                );
            }
        };
        
        textField.getDocument().addDocumentListener(this);
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                if (event.getKeyChar() == KeyEvent.VK_TAB) {
                    stopEditing();
                }
                if (event.getKeyChar() == KeyEvent.VK_ENTER) {
                    stopEditing();
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                }
            }
        });
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(signDelete, BorderLayout.WEST);
        controls.add(signInvalid, BorderLayout.EAST);
        
        signInvalid.setVisible(false);
        signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner());
        textField.add(controls, BorderLayout.EAST);
        
        signDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                commit.accept(textField.getText());
                propHolder.setValue(null);
            }
        });
    }

    @Override
    public Box createEditor() {
        textField = new JTextField() {
            @Override
            public void setForeground(Color fg) {
                super.setForeground(fg);
            }
        };
        textField.setFont(FONT_VALUE);
        textField.setBorder(new EmptyBorder(0, 3, 0, 3));
        textField.addFocusListener(this);

        Box container = new Box(BoxLayout.X_AXIS);
        container.add(textField);
        return container;
    }

    @Override
    public void setPlaceHolder(String text) {
        super.setPlaceHolder(text);
        SwingUtilities.invokeLater(() -> placeHolder.setText(text));
    }

    @Override
    protected void updateEditable(boolean editable) {
        textField.setForeground(editable && !propHolder.isInherited() ? COLOR_NORMAL : COLOR_DISABLED);
        textField.setEditable(editable && !propHolder.isInherited());
        textField.setBackground(editable && !propHolder.isInherited() ? Color.WHITE  : null);
        getEditor().setBackground(editable && !propHolder.isInherited() ? Color.WHITE  : null);
    }

    @Override
    protected void updateValue(String value) {
        textField.getDocument().removeDocumentListener(this);
        textField.setText(value == null ? "" : value);
        textField.getDocument().addDocumentListener(this);
        textField.setCaretPosition(0);
        verify();
    }

    @Override
    public boolean stopEditing() {
        if (propHolder.isInherited()) return true;
        commit.accept(textField.getText());
        return verify();
    }

    private boolean verify() {
        if (signDelete != null) {
            SwingUtilities.invokeLater(() ->signDelete.setVisible(!propHolder.isEmpty() && isEditable() && textField.isFocusOwner()));
        }
        IMask<String> mask = IComplexType.coalesce(propHolder.getPropValue().getMask(), (String text) -> true);
        String value = propHolder.getPropValue().getValue();
        boolean inputOk = ((value == null || value.isEmpty()) && !mask.notNull()) || mask.verify(value);
        SwingUtilities.invokeLater(() -> {
            setBorder(!inputOk ? BORDER_ERROR : textField.isFocusOwner() ? BORDER_ACTIVE : BORDER_NORMAL);
            textField.setForeground(inputOk ? (isEditable() ? COLOR_NORMAL : COLOR_DISABLED) : COLOR_INVALID);
            if (signInvalid != null) {
                signInvalid.setVisible(!inputOk);
            }
        });
        return inputOk;
    }

    @Override
    public void focusGained(FocusEvent event) {
        if (isEditable()) {
            super.focusGained(event);
            verify();
        }
    }
    
    @Override
    public void focusLost(FocusEvent event) {
        if (isEditable()) {
            super.focusLost(event);
            stopEditing();
        }
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата 
     * при вставке текста
     */
    @Override
    public void insertUpdate(DocumentEvent event) {
        signDelete.setVisible(!textField.getText().isEmpty() && isEditable() && textField.isFocusOwner());
        update.accept(textField.getText());
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата
     * при удалении фрагмента текста.
     */
    @Override
    public void removeUpdate(DocumentEvent event) {
        signDelete.setVisible(!textField.getText().isEmpty() && isEditable() && textField.isFocusOwner());
        update.accept(textField.getText());
    }

    /**
     * Вызов метода проверки ввода и сохранения промежуточного результата
     * при редактировании текста.
     */
    @Override
    public void changedUpdate(DocumentEvent event) {
        signDelete.setVisible(!textField.getText().isEmpty() && isEditable() && textField.isFocusOwner());
        update.accept(textField.getText());
    }

    @Override
    public Component getFocusTarget() {
        return textField;
    }
    
    private BalloonTip getErrorTip() {
        IMask<String> mask = propHolder.getPropValue().getMask();
        return new BalloonTip(
                signInvalid, new JLabel(mask.getErrorHint(), ImageUtils.resize(
                    ImageUtils.getByPath("/images/warn.png"), 
                    textField.getPreferredSize().height-2, textField.getPreferredSize().height-2
                ), SwingConstants.LEADING),
                new EdgedBalloonStyle(
                        UIManager.getDefaults().getColor("window"),
                        UIManager.getDefaults().getColor("windowText")
                ),
                BalloonTip.Orientation.RIGHT_ABOVE, 
                BalloonTip.AttachLocation.NORTH,
                5, 5, false
        );
    }

}
