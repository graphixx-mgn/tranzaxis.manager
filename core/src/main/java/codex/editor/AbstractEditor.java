package codex.editor;

import codex.command.EditorCommand;
import codex.command.ICommand;
import codex.command.ICommandListener;
import codex.component.button.PushButton;
import codex.mask.IMask;
import codex.model.Catalog;
import codex.model.Entity;
import codex.property.EditMode;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.type.NullValue;
import codex.utils.ImageUtils;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.TimingUtils;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedList;
import java.util.List;

/**
 * Абстрактный редактор свойств {@link PropertyHolder}. Содержит основные функции
 * управления состоянием виджета и свойства, реализуя роль Controller (MVC).
 */
public abstract class AbstractEditor<T extends IComplexType<V, ? extends IMask<V>>, V> extends JComponent implements IEditor<T, V>, FocusListener {

    private final static Color  HOVER_COLOR   = Color.decode("#D8E6F2");
    private final static Color  PRESS_COLOR   = Color.decode("#C0DCF3");
    
    private final JLabel label;
    protected Box        editor;
    private boolean      editable = true;
    private boolean      locked   = false;
    
    protected final PropertyHolder<T, V> propHolder;
    protected final List<EditorCommand<T, V>> commands = new LinkedList<>();
    private   final List<IEditorListener> listeners = new LinkedList<>();

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public AbstractEditor(PropertyHolder<T, V> propHolder) {
        this.propHolder = propHolder;
        this.label  = new JLabel(propHolder.getTitle());
        this.editor = createEditor();
        
        label.addMouseListener(new MouseAdapter() {
            private BalloonTip tooltipBalloon;
            private final Timer delayTimer = new Timer(1000, (ActionEvent e1) -> {
                if (tooltipBalloon == null && propHolder.getDescription() != null) {
                    tooltipBalloon = new BalloonTip(
                            label, 
                            new JLabel(
                                    propHolder.getDescription(),
                                    ImageUtils.resize(
                                        ImageUtils.getByPath("/images/event.png"), 
                                        16, 16
                                    ), 
                                    SwingConstants.LEADING
                            ),
                            new EdgedBalloonStyle(
                                    UIManager.getDefaults().getColor("window"),
                                    UIManager.getDefaults().getColor("windowText")
                            ),
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

    public final String getPropName() {
        return propHolder.getName();
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

    @Override
    public final V getValue() {
        stopEditing();
        return propHolder.getPropValue().getValue();
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
            if (SwingUtilities.isEventDispatchThread()) {
                updateEditable(isEditable());
            } else {
                SwingUtilities.invokeLater(() -> updateEditable(isEditable()));
            }
        }
    }

    protected void updateEditable(boolean editable) {}
    
    @Override
    public final void setEditable(boolean editable) {
        if (!locked && propHolder.getEditMode() != EditMode.Programmatic) {
            if (this.editable != editable) {
                this.editable = editable;

                new LinkedList<>(listeners).forEach(listener -> listener.setEditable(this.editable));
                if (SwingUtilities.isEventDispatchThread()) {
                    updateEditable(editable);
                } else {
                    SwingUtilities.invokeLater(() -> updateEditable(editable));
                }
            }
        }
    }
    
    @Override
    public final boolean isEditable() {
        return editable && !locked && propHolder.getEditMode() == EditMode.Always;
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

    private void addListener(IEditorListener listener) {
        listeners.add(listener);
    }

    @Override
    public void addCommand(EditorCommand<T, V> command) {
        final EditorCommandButton button = new EditorCommandButton(command);
        commands.add(command);
        editor.add(button);
        command.setContext(propHolder);
    }
    
    @Override
    public List<EditorCommand<T, V>> getCommands() {
        return new LinkedList<>(commands);
    }

    protected abstract void updateValue(V value);

    public final void setValue(V value) {
        SwingUtilities.invokeLater(() -> updateValue(value));
    }

    /**
     * Перерисовка виджета и изменение свойств составных GUI элементов.
     */
    @Override
    public final void updateUI() {
        super.updateUI();
        updateValue(propHolder.getPropValue().getValue());
        updateEditable(isEditable());
        if (editor.isVisible()) {
            getCommands().stream()
                    // Обновляем только команды-потребители или если редактор еще не отображается (начальная инициализация)
                    .filter(command -> command.commandDirection() == EditorCommand.Direction.Consumer || !getEditor().isShowing())
                    .forEach(ICommand::activate);
        }
        label.setFont((propHolder.isValid() ? IEditor.FONT_NORMAL : IEditor.FONT_BOLD));
    }

    class EditorCommandButton extends PushButton {

        EditorCommandButton(EditorCommand<T, V> command) {
            super(command.getIcon(), null);
            setHint(command.getHint());
            setBackground(null);

            button.setBorder(new EmptyBorder(2, 2, 2, 2));
            button.setEnabled(false);
            button.addActionListener(e -> {
                command.execute(command.getContext());
                SwingUtilities.invokeLater(this::updateUI);
            });

            AbstractEditor.this.addListener(new IEditorListener() {
                @Override
                public void setEditable(boolean editable) {
                    if (command.disableWithContext()) {
                        if (editable) {
                            command.activate();
                        } else {
                            button.setEnabled(false);
                        }
                    }
                }

                @Override
                public void setLocked(boolean locked) {
                    if (locked) {
                        button.setEnabled(false);
                    } else {
                        command.activate();
                    }
                }
            });

            command.addListener(new ICommandListener<PropertyHolder<T, V>>() {
                @Override
                public void commandStatusChanged(boolean active, Boolean hidden) {
                    if (hidden != null) {
                        setVisible(!hidden);
                    }
                    button.setEnabled(active && (!command.disableWithContext() || isEditable()));
                }
                @Override
                public void commandIconChanged(ImageIcon icon) {
                    if (icon != null) {
                        button.setIcon(ImageUtils.resize(icon, 20, 20));
                        button.setDisabledIcon(ImageUtils.grayscale(ImageUtils.resize(icon, 20, 20)));
                    }
                }
            });
        }

        @Override
        protected final void redraw() {
            if (button.getModel().isPressed()) {
                setBackground(AbstractEditor.PRESS_COLOR);
            } else if (button.getModel().isRollover()) {
                setBackground(AbstractEditor.HOVER_COLOR);
            } else {
                setBackground(null);
            }
        }
    }

    interface IEditorListener {
        void setEditable(boolean editable);
        void setLocked(boolean locked);
    }


    public class Undefined extends Catalog implements NullValue {

        public Undefined() {
            super(null, ImageUtils.getByPath("/images/clearval.png"), null, null);
            setTitle(propHolder.getPlaceholder());
        }

        @Override
        public Class<? extends Entity> getChildClass() {
            return null;
        }
    }

}
