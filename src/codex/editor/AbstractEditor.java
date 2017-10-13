package codex.editor;

import codex.command.ICommand;
import codex.property.PropertyHolder;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.LinkedList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/**
 * Абстрактный редактор свойств {@link PropertyHolder}. Содержит основные функции
 * управления состоянием виджета и свойства, реализуя роль Controller (MVC).
 */
public abstract class AbstractEditor extends JComponent implements IEditor, FocusListener {
    
    private final JLabel label;
    protected Box        editor;
    private boolean      editable = true;
    
    final PropertyHolder propHolder;
    private final List<ICommand<PropertyHolder>> commands = new LinkedList<>();

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
    }

    @Override
    public final JLabel getLabel() {
        return label;
    }

    @Override
    public final Box getEditor() {
        setBorder(IEditor.BORDER_NORMAL);
        updateUI();
        return editor;
    }

    @Override
    public void setBorder(Border border) {
        editor.setBorder(border);
    };
    
    private static void setToolTipRecursively(JComponent component, String text) {
        component.setToolTipText(text);
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent) {
                setToolTipRecursively((JComponent) child, text);
            }
        }
    }

    /**
     * Установка бордюра в момент получения фокуса ввода.
     * @see IEditor#BORDER_ACTIVE
     */
    @Override
    public void focusGained(FocusEvent event) {
        setBorder(BORDER_ACTIVE);
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
    }
    
    @Override
    public final boolean isEditable() {
        return editable;
    }

    @Override
    public void addCommand(ICommand command) {
        commands.add(command);
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
    public void updateUI() {
        super.updateUI();
        setValue(propHolder.getPropValue().getValue());
        setEditable(isEditable());
        label.setFont((propHolder.isValid() ? IEditor.FONT_NORMAL : IEditor.FONT_BOLD));
    }

}
