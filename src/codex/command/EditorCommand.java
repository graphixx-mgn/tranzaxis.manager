package codex.command;

import codex.component.button.CommandButton;
import codex.component.button.IButton;
import codex.property.PropertyHolder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.ImageIcon;
import javax.swing.JComponent;

/**
 * Абстрактная реализация команд редактора свойств {@link PropertyHolder}.
 * Используется для возможности производить различные действия над свойством.
 */
public abstract class EditorCommand implements ICommand<PropertyHolder, PropertyHolder>, ActionListener {
    
    protected PropertyHolder context;
    protected IButton        button;
    protected Predicate<PropertyHolder> available;
    protected Consumer<PropertyHolder>  activator = (holder) -> {
        button.setEnabled(
                holder != null && (
                    available == null || available.test(holder)
                ) && !holder.isInherited()
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
    public void setContext(PropertyHolder context) {
        this.context = context;
        activate();
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        execute(getContext());
        ((JComponent) button).getParent().getComponent(0).requestFocusInWindow();
    }

    @Override
    public PropertyHolder getContext() {
        return context;
    }

    @Override
    public final boolean multiContextAllowed() {
        return ICommand.super.multiContextAllowed();
    }
    
}
