package codex.command;

import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Абстрактная реализация команды редактора свойств {@link PropertyHolder} сущности.
 * Используется для возможности производить различные действия над свойством: изменение значения свойства или выполнение
 * каких-либо действий, используя значение свойства.
 * @param <T> Прикладной тип, производный от {@link IComplexType}
 * @param <V> Системный тип (Java), хранящийся внутри прикладного типа.
 */
@ThreadSafe
public abstract class EditorCommand<T extends IComplexType<V, ? extends IMask<V>>, V> implements ICommand<
        PropertyHolder<T, V>,
        PropertyHolder<T, V>
    > {

    /**
     * Свойство связанное с редактором которому назначается данная команда.
     */
    protected PropertyHolder<T, V> context;

    private final ImageIcon icon;
    private final String    hint;
    private Predicate<PropertyHolder<T, V>> available;
    private final List<ICommandListener<PropertyHolder<T, V>>> listeners = new LinkedList<>();

    /**
     * Стандартная функция расчета доступности каманды.
     */
    protected Function<PropertyHolder<T, V>, CommandStatus> activator = holder -> new CommandStatus(
            holder != null && (
                    available == null || available.test(holder)
            ) && !holder.isInherited(), getIcon()
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
        this.icon      = ImageUtils.resize(icon, 20, 20);
        this.hint      = hint;
        this.available = available;
    }

    @Override
    public final void addListener(ICommandListener<PropertyHolder<T, V>> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public final void removeListener(ICommandListener<PropertyHolder<T, V>> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Вызывает функцию расчета состояния доступности команды и в зависимости от полученного объекта {@link CommandStatus}
     * вызывает события:<br>
     * * {@link ICommandListener#commandStatusChanged(boolean)}<br>
     * * {@link ICommandListener#commandIconChanged(ImageIcon)}<br>
     * для подписанных слушателей.
     */
    @Override
    public final void activate() {
        CommandStatus status = activator.apply(getContext());
        synchronized (listeners) {
            listeners.forEach(listener -> {
                listener.commandStatusChanged(status.active);
                listener.commandIconChanged(status.icon);
            });
        }
    }

    @Override
    public void setContext(PropertyHolder<T, V> context) {
        this.context = context;
        if (commandDirection() != Direction.Supplier) {
            activate();
        }
    }

    @Override
    public PropertyHolder<T, V> getContext() {
        return context;
    }

    /**
     * Возвращает тип команды. От типа команды зависит алгоритм активации команды.
     */
    public Direction commandDirection() {
        return Direction.Consumer;
    }


    /**
     * Команда редактора всегда исполняется для единственного элемента контекста - свойства.
     */
    @Override
    public final boolean multiContextAllowed() {
        return false;
    }


    /**
     * Возвращает иконку команды, которая будет показываться на кнопке запуска команды.
     */
    @Override
    public final ImageIcon getIcon() {
        return icon;
    }

    /**
     * Возвращает текст подсказки команды, которая будет показана при наведении курсора мыши на кнопку запуска команды.
     * Текст подсказки должен содержать пояснения назначения команды и/или выполняемых ею действий.
     */
    public final String getHint() {
        return hint;
    }

    /**
     * Тип команды.
     */
    public enum Direction {
        /**
         * Команда - поставщик значения для свойства, т.е. при её выполнении значение свойств может быть изменено.
         * Соответственно, при изменении значения свойства нет необходимости в <b>автоматической</b> активации команды, которая
         * может быть в общем случае длительной.
         */
        Supplier,
        /**
         * Команда - потребитель значения свойства, и при его изменении требуется обязательно пересчитать состояние команды.
         */
        Consumer
    }
}
