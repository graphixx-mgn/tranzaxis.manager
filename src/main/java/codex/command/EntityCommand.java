package codex.command;

import codex.explorer.tree.AbstractNode;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.Language;
import javax.swing.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Абстрактная реализация команд сущности {@link Entity}.
 * Используется для возможности производить различные действия над сущностью.
 * @param <V> Класс {@link Entity} или один из его производных.
 */
public abstract class EntityCommand<V extends Entity> implements ICommand<V, List<V>>, Iconified {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));
    
    /**
     * Тип команды. 
     */
    public enum Kind {
        /**
         * Административная команда. Не отображается в модуле ярлыков.
         */
        Admin,
        /**
         * Информационная команда. Не отображается в модуле ярлыков и блокируется вместе с сущностью.
         * Команда такого типа не должна менять значения сущности.
         */
        Info,
        /**
         * Команда, запускающая действие над сущностью. Отображается в модуле ярлыков.
         */
        Action,
        /**
         * Команда родительской сущности. Встраивается в презентацию селектора как системная команда. Контекст команды - родительская
         * сущность элементов селектора.
         */
        System
    }

    private final KeyStroke key;
    private final String    name;
    private final String    hint;
    private final ImageIcon icon;
    private final String    title;
    private List<V>   context = new LinkedList<>();
    private String    groupId;
    private Predicate<V> available;
    private final List<ICommandListener<V>> listeners = new LinkedList<>();
    private Supplier<PropertyHolder[]>      provider  = () -> new PropertyHolder[]{};

    /**
     * Функция расчета доступности каманды.
     */
    protected Function<List<V>, CommandStatus> activator = entities -> new CommandStatus(
        entities != null && entities.size() > 0 &&
              !(entities.size() > 1 && !multiContextAllowed()) && (
                      available == null || entities.stream().allMatch(available)
              ) &&
              entities.stream().noneMatch(AbstractNode::islocked)
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

    @Override
    public final ImageIcon getIcon() {
        return icon;
    }

    public final String getHint() {
        return hint;
    }

    /**
     * Возвращает код комбинации клавиш клавиатуры для запуска команды.
     */
    public final KeyStroke getKey() {
        return key;
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

    /**
     * Проверка доступности команды для текущего контекста.
     */
    public boolean isActive() {
        return activator.apply(getContext()).isActive();
    }

    @Override
    public final void addListener(ICommandListener<V> listener) {
        listeners.add(listener);
    }

    @Override
    public final void removeListener(ICommandListener<V> listener) {
        listeners.remove(listener);
    }

    @Override
    public final void setContext(List<V> context) {
        this.context.forEach((contextItem) -> contextItem.model.removeModelListener(modelListener));
        this.context = context;
        new LinkedList<>(listeners).forEach((listener) -> listener.contextChanged(context));
        this.context.forEach((contextItem) -> contextItem.model.addModelListener(modelListener));
        activate();
    }
    
    public final void setContext(V context) {
        setContext(Collections.singletonList(context));
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
     * Установка списка пераметров команды.
     * @param propHolders Список объектов {@link PropertyHolder} произвольной длины.
     */
    public EntityCommand setParameters(PropertyHolder... propHolders) {
        if (propHolders != null && propHolders.length > 0) {
            provider = () -> propHolders;
        }
        return this;
    }

    /**
     * Получение вызов диалога заполнения параметров и возврат значений.
     */
    public final Map<String, IComplexType> getParameters() {
        PropertyHolder[] params = provider.get();
        if (params.length == 0) {
            return new HashMap<>();
        }
        ParametersDialog paramDialog = new ParametersDialog(this, provider);
        try {
            return paramDialog.call();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Установка имени группы для команды. Команды одной группы объединяются в меню.
     * @param groupId Строковый идентификатор.
     */
    public final EntityCommand setGroupId(String groupId) {
        if (groupId != null) {
            this.groupId = groupId;
        }
        return this;
    }
    
    /**
     * Получение имени группы команды.
     */
    public final String getGroupId() {
        return groupId;
    }
    
    @Override
    @Deprecated
    public final void execute(V context) {}
    
    public abstract void execute(V context, Map<String, IComplexType> params);
    
    /**
     * Исполнение длительной задачи над сущностью с блокировкой.
     * @param context Сущность.
     * @param task Задача.
     * @param foreground Исполнить в модальном диалоге.
     */
    public final void executeTask(V context, ITask task, boolean foreground) {
        task.addListener(new ITaskListener() {
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
        });

        if (foreground) {
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
