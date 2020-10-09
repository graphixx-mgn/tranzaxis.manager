package codex.command;

import codex.log.Logger;
import codex.model.Entity;
import codex.type.IComplexType;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Абстрактная реализация групповой команды сущности {@link Entity}. Единственное отличие команд этого класса от
 * {@link EntityCommand} - команда исполняется один раз для всего набора сущностей, составляющих контекст команды
 * на момент её запуска. В то время как код исполнения команд типа {@link EntityCommand} имеет доступ только к
 * каждому элементу контекста в отдельности.
 * @param <V> Класс {@link Entity} или один из его производных.
 */
@ThreadSafe
public abstract class EntityGroupCommand<V extends Entity> extends EntityCommand<V> {

    /**
     * Конструктор экземпляра команды.
     * @param name Идентификатор команды.
     * @param title Подпись кнопки запуска команды.
     * @param icon Иконка устанавливаемая на кнопку запуска команды, не может быть NULL.
     * @param hint Описание команды, отображается при наведении мыши на кнопку.
     * @param available Функция проверки доступности команды.
     */
    public EntityGroupCommand(String name, String title, ImageIcon icon, String hint, Predicate<V> available) {
        super(name, title, icon, hint, available);
    }

    @Override
    protected final void process() {
        List<V> context = getContext();
        if (!context.isEmpty()) {
            Logger.getLogger().debug("Perform command [{0}]. Group context: {1}", getName(), context);
            try {
                execute(context, getParameters());
            } catch (ParametersDialog.Canceled e) {
                // Do not call command
            }
        }
        activate();
    }

    @Override
    public final boolean multiContextAllowed() {
        return true;
    }

    @Override
    public final void execute(V context, Map<String, IComplexType> params) {
        throw new UnsupportedOperationException();
    }

    /**
     * Метод исполнения команды, содержащий непосредственно код, реализующий назначение команды.
     * Требуется перекрытие в классах-наследниках. Если контекстом команды является несколько сущностей, данный
     * метод будет последовательно вызван для каждой из них.
     * @param context Список сущностей, установленных в качестве контекста команды.
     * @param params Карта параметров команды, заполненная значениями, введенными пользователем.
     */
    public abstract void execute(List<V> context, Map<String, IComplexType> params);
}
