package codex.command;

import codex.explorer.tree.AbstractNode;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.IComplexType;
import codex.type.ISerializableType;
import codex.type.Iconified;
import codex.utils.Language;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import java.lang.annotation.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Абстрактная реализация команд сущности {@link Entity}.
 * Используется для возможности производить различные действия над сущностью.
 * @param <V> Класс {@link Entity} или один из его производных.
 */
@ThreadSafe
@EntityCommand.Definition(parentCommand = EntityCommand.class)
public abstract class EntityCommand<V extends Entity> implements ICommand<V, Collection<V>>, Iconified {
    
    private static final ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    /**
     * Аннотацию следует использовать для указания "иерархии" команд. Если у класса команды указана данная аннотация,
     * то при постоении панели команд {@link codex.presentation.CommandPanel} в презентации сущности данная команда
     * будет размещена в выпадающем меню указанной "родительской" команды. Таким образом производится группировка
     * команд, схожих по назначению. В качестве "родительской" команды можно указывать наиболее часто используемую пользователем.
     */
    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Definition {
        Class<? extends EntityCommand> parentCommand();
    }

    /**
     * Тип команды. 
     */
    public enum Kind {
        /**
         * Административная команда. Встраивается в презентацию селектора. Контекст команды -
         * выбранные сущности элементов селектора. Кроме этого, встраивается и как команда редактора поля типа EntityRef.
         */
        Admin,
        /**
         * Команда, запускающая действие над сущностью. Встраивается в презентацию селектора. Контекст команды -
         * выбранные сущности элементов селектора.
         */
        Action,
        /**
         * Команда родительской сущности. Встраивается в презентацию селектора как системная команда. Контекст команды -
         * родительская сущность элементов селектора.
         */
        System
    }

    private final KeyStroke key;
    private final String    name;
    private final String    hint;
    private final ImageIcon icon;
    private final String    title;
    private Collection<V>   context = new LinkedList<>();
    private Predicate<V> available;
    private final List<ICommandListener<V>> listeners = new LinkedList<>();
    private Supplier<PropertyHolder[]>      provider  = () -> new PropertyHolder[]{};

    /**
     * Стандартная функция расчета доступности каманды.
     */
    protected Function<List<V>, CommandStatus> activator = entities -> new CommandStatus(
        entities != null && entities.size() > 0 &&
              !(entities.size() > 1 && !multiContextAllowed()) && (
                    available == null || entities.stream().allMatch(available)
              ) && (
                    entities.stream().noneMatch(AbstractNode::islocked) || !disableWithContext()
              )
    );

    private final IModelListener modelListener = new IModelListener() {
        @Override
        public void modelChanged(EntityModel model, List<String> changes) {
            activate();
        }

        @Override
        public void modelSaved(EntityModel model, List<String> changes) {
            activate();
        }

        @Override
        public void modelRestored(EntityModel model, List<String> changes) {
            activate();
        }
    };

    
    /**
     * Конструктор экземпляра команды.
     * @param name Идентификатор команды.
     * @param title Подпись кнопки запуска команды.
     * @param icon Иконка устанавливаемая на кнопку запуска команды, не может быть NULL.
     * @param hint Описание команды, отображается при наведении мыши на кнопку.
     * @param available Функция проверки доступности команды.
     */
    public EntityCommand(String name, String title, ImageIcon icon, String hint, Predicate<V> available) {
        this(name, title, icon, hint, available, null);
    }
    
    /**
     * Конструктор экземпляра команды.
     * @param name Идентификатор команды.
     * @param title Подпись кнопки запуска команды.
     * @param icon Иконка устанавливаемая на кнопку запуска команды, не может быть NULL.
     * @param hint Описание команды, отображается при наведении мыши на кнопку.
     * @param available Функция проверки доступности команды.
     * @param key Код комбинации клавиш клавиатуры для запуска команды.
     */
    public EntityCommand(String name, String title, ImageIcon icon, String hint, Predicate<V> available, KeyStroke key) {
        if (icon == null) {
            throw new IllegalStateException("Parameter 'icon' can not be NULL");
        }
        this.key       = key;
        this.icon      = icon;
        this.hint      = hint;
        this.name      = name == null ? getClass().getCanonicalName().toLowerCase() : name;
        this.available = available;
        
        if (title != null) {
            String localTitle = Language.get(this.getClass(), title);
            this.title = localTitle.equals(Language.NOT_FOUND) ? title : localTitle;
        } else {
            this.title = Language.NOT_FOUND;
        }
    }

    
    /**
     * Возвращает тип команды (По-умолчанию - {@link Kind#Action}).
     */
    public Kind getKind() {
        return Kind.Action;
    }
    
    /**
     * Возвращает имя (ID) команды.
     */
    public final String getName() {
        return name;
    }

    /**
     * Возвращает название команды.
     */
    public final String getTitle() {
        return title;
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
     * Возвращает код комбинации клавиш клавиатуры для запуска команды.
     */
    public final KeyStroke getKey() {
        return key;
    }

    /**
     * Вызывает функцию расчета состояния доступности команды и в зависимости от полученного объекта {@link CommandStatus}
     * вызывает события:<br>
     * * {@link ICommandListener#commandStatusChanged(boolean, Boolean)}<br>
     * * {@link ICommandListener#commandIconChanged(ImageIcon)}<br>
     * для подписанных слушателей.
     */
    @Override
    public final void activate() {
        CommandStatus status = activator.apply(getContext());
        synchronized (listeners) {
            listeners.forEach(listener -> {
                listener.commandStatusChanged(status.active, status.hidden);
                if (status.icon != null) {
                    listener.commandIconChanged(status.icon);
                }
            });
        }
    }

    /**
     * Проверка доступности команды для текущего контекста.
     */
    public boolean isActive() {
        return activator.apply(getContext()).isActive();
    }

    @Override
    public final void addListener(ICommandListener<V> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public final void removeListener(ICommandListener<V> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public final void setContext(Collection<V> context) {
        this.context.forEach((contextItem) -> contextItem.model.removeModelListener(modelListener));
        this.context = context;
        synchronized (listeners) {
            listeners.forEach((listener) -> listener.contextChanged(context));
        }
        this.context.forEach((contextItem) -> contextItem.model.addModelListener(modelListener));
        activate();
    }

    /**
     * Текст вопроса, отображаемого пользователю для подтверждения перед исполнением команды.
     * Если запрос не требуется, метод должен вернуть NULL.
     */
    public String acquireConfirmation() {
        return null;
    }
    
    /**
     * Предобработка модели параметров команды. Может использоваться для более
     * гибкой настройки редакторов параметров и их взаимосвязей.
     * @param paramModel Модель набора параметров.
     */
    protected void preprocessParameters(ParamModel paramModel) {
        // Do nothing
    }
    
    /**
     * Установка списка пераметров команды. Перед вызовом команды, если параметры заданы, будет показан диалог
     * для ввода их значений.
     * @param propHolders Список объектов {@link PropertyHolder} произвольной длины.
     */
    protected void setParameters(PropertyHolder... propHolders) {
        if (propHolders != null && propHolders.length > 0) {
            provider = () -> propHolders;
        }
    }

    boolean hasParameters() {
        return provider.get().length > 0;
    }

    Collection<PropertyHolder> getParameterProperties() {
        return provider.get().length == 0 ?
               Collections.emptyList() :
               Arrays.stream(provider.get())
                    .filter(propertyHolder -> propertyHolder.getPropValue() instanceof ISerializableType)
                    .collect(Collectors.toList());
    }

    /**
     * Вызов диалога для заполнения параметров и возврат значений.
     */
    public final Map<String, IComplexType> getParameters() throws ParametersDialog.Canceled {
        if (provider.get().length > 0) {
            ParametersDialog paramDialog = new ParametersDialog(this, provider);
            return paramDialog.getProperties().stream()
                    .collect(Collectors.toMap(
                            PropertyHolder::getName,
                            PropertyHolder::getOwnPropValue
                    ));
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Фабричный метод создания кнопок "родительских" команд.
     */
    public IGroupButtonFactory groupButtonFactory() {
        return GroupCommandButton::new;
    }

    /**
     * Запуск поцесса исполнения команды.
     */
    public void process() {
        List<V> context = getContext();
        if (context.isEmpty() && isActive()) {
            Logger.getLogger().debug("Perform contextless command [{0}]", getName());
            execute(null, null);
        } else {
            try {
                final Map<String, IComplexType> params = getParameters();
                Logger.getLogger().debug(
                        "Perform command [{0}]. Context: {1}",
                        getName(),
                        context.size() == 1 ?
                                context.get(0) :
                                context.stream()
                                        .map(entity -> "\n * "+entity.model.getQualifiedName()).collect(Collectors.joining())
                );
                context.forEach(entity -> {
                    ITask task = getTask(entity, params);
                    if (task != null) {
                        executeTask(entity, task);
                    } else {
                        execute(entity, params);
                    }
                });
            } catch (ParametersDialog.Canceled e) {
                // Do not call command
            }
        }
        activate();
    }
    
    @Override
    @Deprecated
    public final void execute(V context) {
        throw new UnsupportedOperationException();
    }

    /**
     * Метод исполнения команды, содержащий непосредственно код, реализующий назначение команды.
     * Требуется перекрытие в классах-наследниках. Если контекстом команды является несколько сущностей, данный
     * метод будет последовательно вызван для каждой из них.
     * @param context Элемент набора объектов, установленных в качестве контекста команды.
     * @param params Карта параметров команды, заполненная значениями, введенными пользователем.
     */
    public abstract void execute(V context, Map<String, IComplexType> params);

    /**
     * Реализация логики команды в виде задачи. Если команда перекрывает данный метод, будет запущен метод
     * {@link EntityCommand#executeTask(Entity, ITask)}.
     * @param context Контекстная сущность
     */
    public ITask getTask(V context, Map<String, IComplexType> params) {
        return null;
    }

    /**
     * Исполнение длительной задачи над сущностью с блокировкой. Вспомогательный метод, порождающий задачу, которая
     * исполняется сервисом выполнения задач {@link ITaskExecutorService}.
     * @param context Элемент набора объектов, установленных в качестве контекста команды.
     * @param task Задача, коорая будет передана сервису.
     * (см. {@link ITaskExecutorService#enqueueTask(ITask)}).
     */
    protected void executeTask(V context, ITask task) {
        ITaskListener lockHandler = new ITaskListener() {
            @Override
            public void beforeExecute(ITask task) {
                if (context != null && !context.islocked()) {
                    try {
                        context.getLock().acquire();
                    } catch (InterruptedException e) {
                        //
                    }
                }
            }

            @Override
            public void afterExecute(ITask task) {
                if (context != null) {
                    context.getLock().release();
                }
            }
        };
        task.addListener(lockHandler);
        if (!getContext().isEmpty() && getKind() == Kind.Admin) {
            TES.executeTask(task);
        } else {
            TES.enqueueTask(task);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final List<V> getContext() {
        return context == null ? new LinkedList<>() : new LinkedList(context);
    }
    
    @Override
    public String toString() {
        return IComplexType.coalesce(title, name);
    }

}
