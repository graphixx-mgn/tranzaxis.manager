package codex.command;

import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Абстрактная реализация команды редактора свойств {@link PropertyHolder}.
 * Используется для возможности производить различные действия над свойством.
 */
public abstract class EditorCommand<T extends IComplexType<V, ? extends IMask<V>>, V> implements ICommand<
        PropertyHolder<T, V>,
        PropertyHolder<T, V>
    > {

    /**
     * Свойство связанное с данным редактором.
     */
    protected PropertyHolder<T, V> context;

    private final ImageIcon icon;
    private final String    hint;
    private Predicate<PropertyHolder<T, V>> available;
    private final List<ICommandListener<PropertyHolder<T, V>>> listeners = new LinkedList<>();

    /**
     * Функция расчета доступности каманды.
     */
    protected Function<PropertyHolder<T, V>, CommandStatus> activator = holder -> new CommandStatus(
            holder != null && (
                    available == null || available.test(holder)
            ) &&
            !holder.isInherited()
    );
    
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
    public EditorCommand(ImageIcon icon, String hint, Predicate<PropertyHolder<T, V>> available) {
        if (icon == null) {
            throw new IllegalStateException("Parameter 'icon' can not be NULL");
        }
        this.icon      = icon;
        this.hint      = hint;
        this.available = available;
    }

    @Override
    public final void addListener(ICommandListener<PropertyHolder<T, V>> listener) {
        listeners.add(listener);
    }

    @Override
    public final void removeListener(ICommandListener<PropertyHolder<T, V>> listener) {
        listeners.remove(listener);
    }
    
    @Override
    public final void activate() {
        CommandStatus status = activator.apply(getContext());
        new LinkedList<>(listeners).forEach(listener -> {
            listener.commandStatusChanged(status.active);
            if (status.icon != null) {
                listener.commandIconChanged(status.icon);
            }
        });
    }

    @Override
    public void setContext(PropertyHolder<T, V> context) {
        this.context = context;
        activate();
    }

    @Override
    public PropertyHolder<T, V> getContext() {
        return context;
    }

    @Override
    public final boolean multiContextAllowed() {
        return false;
    }

    @Override
    public final ImageIcon getIcon() {
        return icon;
    }

    public final String getHint() {
        return hint;
    }
}
