package codex.command;

import codex.component.button.CommandButton;
import codex.component.button.IButton;
import codex.property.PropertyHolder;
import codex.type.FilePath;
import codex.type.StringList;
import codex.utils.ImageUtils;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;

/**
 * Абстрактная реализация команд над свойствами {@link PropertyHolder}.
 * Используется для возможности производить различные действия над свойством.
 * В частности, таким образом реализуется запуск редакторов таких типов как
 * {@link FilePath} и {@link StringList}.
 */
public abstract class PropertyCommand implements ICommand<PropertyHolder>, ActionListener {
    
    protected PropertyHolder[] context;
    protected IButton          button;
    
    /**
     * Конструктор экземпляра команды.
     * @param icon Иконка устанавливаемая на кнопку запуска команды, не может быть NULL.
     * @param title Описание команды, отображается при наведении мыши на кнопку.
     */
    public PropertyCommand(ImageIcon icon, String title) {
        if (icon == null) {
            throw new IllegalStateException("Parameter 'icon' can not be NULL");
        }
        this.button = new CommandButton(ImageUtils.resize(icon, 20, 20));
        this.button.addActionListener(this);
        this.button.setHint(title);
    }
    
    
    @Override
    public IButton getButton() {
        return button;
    }

    @Override
    public void setContext(PropertyHolder... context) {
        this.context = context;
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        for (PropertyHolder propHolder : context) {
            execute(propHolder);
        }
    }

    @Override
    public PropertyHolder[] getContext() {
        return context;
    }
    
}
