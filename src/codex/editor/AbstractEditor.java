package codex.editor;

import codex.command.EditorCommand;
import codex.command.ICommand;
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
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.TimingUtils;

/**
 * Абстрактный редактор свойств {@link PropertyHolder}. Содержит основные функции
 * управления состоянием виджета и свойства, реализуя роль Controller (MVC).
 */
public abstract class AbstractEditor extends JComponent implements IEditor, FocusListener {
    
    private final JLabel label;
    protected Box        editor;
    private boolean      editable = true;
    private boolean      locked   = false;
    
    protected final PropertyHolder propHolder;
    protected final List<ICommand<PropertyHolder, PropertyHolder>> commands = new LinkedList<>();

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public AbstractEditor(PropertyHolder propHolder) {
        this.propHolder = propHolder;
        this.label  = new JLabel(propHolder.getTitle());
        this.editor = createEditor();
        
        label.addMouseListener(new MouseAdapter() {
            BalloonTip tooltipBalloon;
            Timer delayTimer = new Timer(1000, (ActionEvent e1) -> {
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
        
        propHolder.addChangeListener((String name, Object oldValue, Object newValue) -> {
            updateUI();
        });
        propHolder.addStateListener((name) -> {
            updateUI();
        });
        
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
    };

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
    
    public void setLocked(boolean locked) {
        this.locked = locked;
        setEditable(isEditable());
        super.updateUI();
    }
    
    @Override
    public void setEditable(boolean editable) {
        if (!locked) {
            this.editable = editable;
        }
        commands.stream().filter((command) -> {
            return command.disableWithContext();
        }).forEach((command) -> {
            command.getButton().setEnabled(editable);
            if (editable) {
                command.activate();
            }
        });
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
    };

    @Override
    public void addCommand(EditorCommand command) {
        commands.add(command);
        command.getButton().addActionListener((e) -> {
            SwingUtilities.invokeLater(() -> {
                updateUI();
            });
        });
        command.setContext(propHolder);
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
    
    public class NullValue implements Iconified {
        
        ImageIcon ICON = ImageUtils.getByPath("/images/clearval.png");

        @Override
        public ImageIcon getIcon() {
            return AbstractEditor.this.isEditable() ? ICON : ImageUtils.grayscale(ICON);
        }
        
        @Override
        public String toString() {
            return propHolder.getPlaceholder();
        }
    }

}
