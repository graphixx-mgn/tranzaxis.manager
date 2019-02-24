package codex.editor;

import codex.command.EditorCommand;
import codex.command.ICommand;
import codex.command.ICommandListener;
import codex.component.button.PushButton;
import codex.property.PropertyHolder;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.TimingUtils;

/**
 * Абстрактный редактор свойств {@link PropertyHolder}. Содержит основные функции
 * управления состоянием виджета и свойства, реализуя роль Controller (MVC).
 */
public abstract class AbstractEditor extends JComponent implements IEditor, FocusListener {

    private static final Border NORMAL_BORDER = new CompoundBorder(
            new MatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY),
            new EmptyBorder(1, 0, 1, 1)
    );
    
    private final JLabel label;
    protected Box        editor;
    private boolean      editable = true;
    private boolean      locked   = false;
    
    protected final PropertyHolder propHolder;
    protected final List<ICommand<PropertyHolder, PropertyHolder>> commands = new LinkedList<>();
    private   final List<IEditorListener> listeners = new LinkedList<>();

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public AbstractEditor(PropertyHolder propHolder) {
        this.propHolder = propHolder;
        this.label  = new JLabel(propHolder.getTitle());
        this.editor = createEditor();
        
        label.addMouseListener(new MouseAdapter() {
            private BalloonTip tooltipBalloon;
            private final Timer delayTimer = new Timer(1000, (ActionEvent e1) -> {
                if (tooltipBalloon == null) {
                    tooltipBalloon = new BalloonTip(
                            label, 
                            new JLabel(
                                    propHolder.getDescriprion(), 
                                    ImageUtils.resize(
                                        ImageUtils.getByPath("/images/event.png"), 
                                        16, 16
                                    ), 
                                    SwingConstants.LEADING
                            ), 
                            new EdgedBalloonStyle(Color.WHITE, Color.GRAY), 
                            false
                    );
                    TimingUtils.showTimedBalloon(tooltipBalloon, 4000);
                } 
            }) {{
                setRepeats(false);
            }};
            
            @Override
            public void mouseEntered(MouseEvent e) {
                delayTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (delayTimer.isRunning()) {
                    delayTimer.stop();
                }
                if (tooltipBalloon != null) {
                    tooltipBalloon.closeBalloon();
                    tooltipBalloon = null;
                }
            }
        });
        
        propHolder.addChangeListener((String name, Object oldValue, Object newValue) -> updateUI());
        propHolder.addStateListener((name) -> updateUI());
        
        setBorder(IEditor.BORDER_NORMAL);
        updateUI();
    }

    @Override
    public final JLabel getLabel() {
        return label;
    }

    @Override
    public Box getEditor() {
        return editor;
    }

    @Override
    public final void setBorder(Border border) {
        editor.setBorder(border);
    }

    /**
     * Установка бордюра в момент получения фокуса ввода.
     * @see IEditor#BORDER_ACTIVE
     */
    @Override
    public void focusGained(FocusEvent event) {
        if (isEditable()) {
            setBorder(BORDER_ACTIVE);
        }
    }

    /**
     * Возврат обычного бордюра в момент потери фокуса ввода.
     * @see IEditor#BORDER_ACTIVE
     */
    @Override
    public void focusLost(FocusEvent event) {
        setBorder(BORDER_NORMAL);
    }
    
    /**
     * Переключение состояние блокировки редактора - временная недоступность
     * пока сущность заблокирована.
     * @param locked Если TRUE - значение редактировать невозможно.
     */
    public void setLocked(boolean locked) {
        if (this.locked != locked) {
            this.locked = locked;
            new LinkedList<>(listeners).forEach(listener -> listener.setLocked(this.locked));
            setEditable(isEditable());
        }
    }
    
    @Override
    public void setEditable(boolean editable) {
        if (!locked) {
            if (this.editable != editable) {
                this.editable = editable;
                new LinkedList<>(listeners).forEach(listener -> listener.setEditable(this.editable));
            }
        }
    }
    
    @Override
    public final boolean isEditable() {
        return editable && !locked;
    }
    
    @Override
    public void setVisible(boolean visible) {
        boolean prevValue = editor.isVisible();
        editor.setVisible(visible);
        editor.firePropertyChange("visible", prevValue, visible);
    }
    
    @Override
    public boolean isVisible() {
        return editor.isVisible();
    }

    void addListener(IEditorListener listener) {
        listeners.add(listener);
    }

    @Override
    public void addCommand(EditorCommand command) {
        final EditorCommandButton button = new EditorCommandButton(command);
        commands.add(command);
        editor.add(button);
        command.setContext(propHolder);
        updateUI();
    }
    
    @Override
    public List<ICommand<PropertyHolder, PropertyHolder>> getCommands() {
        return new LinkedList<>(commands);
    }

    /**
     * Перерисовка виджета и изменение свойств составных GUI элементов.
     */
    @Override
    public final void updateUI() {
        super.updateUI();
        setValue(propHolder.getPropValue().getValue());
        setEditable(isEditable());
        label.setFont((propHolder.isValid() ? IEditor.FONT_NORMAL : IEditor.FONT_BOLD));
    }
    
    /**
     * Класс, используемый при отрисовке NULL значения в списках. 
     */
    public class NullValue implements Iconified {
        
        private final ImageIcon ICON = ImageUtils.getByPath("/images/clearval.png");

        @Override
        public ImageIcon getIcon() {
            return AbstractEditor.this.isEditable() ? ICON : ImageUtils.grayscale(ICON);
        }
        
        @Override
        public String toString() {
            return propHolder.getPlaceholder();
        }
    }


    class EditorCommandButton extends PushButton {

        EditorCommandButton(EditorCommand command) {
            super(command.getIcon(), null);
            button.setBorder(new EmptyBorder(2, 2, 2, 2));
            setHint(command.getHint());
            setBackground(null);
            setBorder(NORMAL_BORDER);

            button.addActionListener(e -> {
                command.execute(command.getContext());
                SwingUtilities.invokeLater(() -> {
                    if (!locked) commands.forEach(ICommand::activate);
                    updateUI();
                });
            });

            AbstractEditor.this.addListener(new IEditorListener() {
                @Override
                public void setEditable(boolean editable) {
                    if (command.disableWithContext()) button.setEnabled(editable);
                }

                @Override
                public void setLocked(boolean locked) {
                    button.setEnabled(!locked);
                }
            });

            command.addListener(new ICommandListener<PropertyHolder>() {
                @Override
                public void commandStatusChanged(boolean active) {
                    button.setEnabled(active);
                }

                @Override
                public void commandIconChanged(ImageIcon icon) {
                    button.setIcon(icon);
                    button.setDisabledIcon(ImageUtils.grayscale(icon));
                }
            });
        }

        @Override
        protected final void redraw() {
            if (button.getModel().isPressed()) {
                setBorder(PRESS_BORDER);
                setBackground(PRESS_COLOR);
            } else if (button.getModel().isRollover()) {
                setBorder(HOVER_BORDER);
                setBackground(HOVER_COLOR);
            } else {
                setBorder(NORMAL_BORDER);
                setBackground(null);
            }
        }
    }

    interface IEditorListener {

        void setEditable(boolean editable);
        void setLocked(boolean locked);

    }

}
