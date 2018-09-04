package codex.editor;

import codex.command.ICommand;
import codex.property.PropertyHolder;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.LinkedList;
import java.util.List;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import net.java.balloontip.BalloonTip;
import net.java.balloontip.styles.EdgedBalloonStyle;
import net.java.balloontip.utils.ToolTipUtils;

/**
 * Абстрактный редактор свойств {@link PropertyHolder}. Содержит основные функции
 * управления состоянием виджета и свойства, реализуя роль Controller (MVC).
 */
public abstract class AbstractEditor extends JComponent implements IEditor, FocusListener {
    
    private final JLabel label;
    protected Box        editor;
    private boolean      editable = true;
    
    protected final PropertyHolder propHolder;
    protected final List<ICommand<PropertyHolder>> commands = new LinkedList<>();

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public AbstractEditor(PropertyHolder propHolder) {
        this.propHolder = propHolder;
        this.label  = new JLabel(propHolder.getTitle());
        this.editor = createEditor();
        
        setToolTipRecursively(editor, propHolder.getDescriprion());
        propHolder.addChangeListener((String name, Object oldValue, Object newValue) -> {
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
    
    private static void setToolTipRecursively(JComponent component, String text) {
        component.setToolTipText(text);
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent) {
                BalloonTip tooltipBalloon = new BalloonTip(
                        (JComponent) child, 
                        new JLabel(text, ImageUtils.resize(
                            ImageUtils.getByPath("/images/event.png"), 
                            16, 16
                        ), SwingConstants.LEADING), 
                        new EdgedBalloonStyle(Color.WHITE, Color.GRAY), 
                        false
                );
                ToolTipUtils.balloonToToolTip(tooltipBalloon, 2000, 3000);
            }
        }
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
    
    @Override
    public void setEditable(boolean editable) {
        this.editable = editable;
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
        return editable;
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
    public void addCommand(ICommand command) {
        commands.add(command);
        command.getButton().addActionListener((e) -> {
            SwingUtilities.invokeLater(() -> {
                updateUI();
            });
        });
        ((ICommand<PropertyHolder>)command).setContext(propHolder);
    }
    
    @Override
    public List<ICommand<PropertyHolder>> getCommands() {
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
            return NOT_DEFINED;
        }
    }

}
