package codex.command;

import codex.component.button.CommandButton;
import codex.component.button.IButton;
import codex.property.PropertyHolder;
import codex.type.FilePath;
import codex.type.StringList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.ImageIcon;

/**
 * Абстрактная реализация команд редактора свойств {@link PropertyHolder}.
 * Используется для возможности производить различные действия над свойством.
 * В частности, таким образом реализуется запуск редакторов таких типов как
 * {@link FilePath} и {@link StringList}.
 */
public abstract class EditorCommand implements ICommand<PropertyHolder>, ActionListener {
    
    protected PropertyHolder[] context;
    protected IButton          button;
    protected Predicate<PropertyHolder> available;
    protected Consumer<PropertyHolder[]> activator = (holders) -> {
        button.setEnabled(
                holders != null && holders.length > 0 && 
                !(holders.length > 1 && !multiContextAllowed()) && (
                        available == null || Arrays.asList(holders).stream().allMatch(available)
                )
        );
    };
    
    /**
     * Конструктор экземпляра команды.
     * @param icon Иконка устанавливаемая на кнопку запуска команды, не может быть NULL.
     * @param hint Описание команды, отображается при наведении мыши на кнопку.
     */
    public EditorCommand(ImageIcon icon, String hint) {
        this(icon, hint, null);
    }
    
    /**
     * Конструктор экземпляра команды.
     * @param icon Иконка устанавливаемая на кнопку запуска команды, не может быть NULL.
     * @param hint Описание команды, отображается при наведении мыши на кнопку.
     * @param available Предикат определяющий доступность команды.
     */
    public EditorCommand(ImageIcon icon, String hint, Predicate<PropertyHolder> available) {
        if (icon == null) {
            throw new IllegalStateException("Parameter 'icon' can not be NULL");
        }
        this.available = available;
        this.button = new CommandButton(icon);
        this.button.addActionListener(this);
        this.button.setHint(hint);
    }
    
    @Override
    public IButton getButton() {
        return button;
    }
    
    @Override
    public final void activate() {
        activator.accept(getContext());
    };

    @Override
    public void setContext(PropertyHolder... context) {
        if (context.length > 1 && !multiContextAllowed()) {
            throw new IllegalStateException("Multiple context is not allowed");
        }
        this.context = context;
        activate();
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

    @Override
    public final boolean multiContextAllowed() {
        return ICommand.super.multiContextAllowed();
    }
    
}
