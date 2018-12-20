package codex.command;

import codex.component.button.IButton;
import codex.component.button.PushButton;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.model.ParamModel;
import codex.presentation.CommitEntity;
import codex.presentation.RollbackEntity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.Language;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Абстрактная реализация команд сущности {@link Entity}.
 * Используется для возможности производить различные действия над сущностью.
 * @param <V> Класс {@link Entity} или один из его производных.
 * @see CommitEntity
 * @see RollbackEntity
 */
public abstract class EntityCommand<V extends Entity> implements ICommand<V, List<V>>, ActionListener, IModelListener, ICommandListener<V>, Iconified {
    
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
         * Команда, запускающая действие над сущностью.
         */
        Action
    }
    
    private KeyStroke key;
    private String    name;
    private String    title;
    private List<V>   context = new LinkedList<>();
    private IButton   button; 
    private String    groupId;
    private final List<ICommandListener<V>> listeners = new LinkedList<>();
    private Supplier<PropertyHolder[]>      provider  = () -> { return new PropertyHolder[] {}; };
    
    protected Predicate<V>      available;
    protected Consumer<List<V>> activator = (entities) -> {
        button.setEnabled(
                entities != null && entities.size() > 0 && 
                !(entities.size() > 1 && !multiContextAllowed()) && 
                (available == null || entities.stream().allMatch(available)) && 
                entities.stream().allMatch((entity) -> {
                    return !entity.islocked();
                })
        );
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
        this.name      = name;
        this.available = available;
        
        if (title != null) {
            String localTitle = Language.get(this.getClass().getSimpleName(), title);
            this.title = localTitle.equals(Language.NOT_FOUND) ? title : localTitle;
        }
        
        this.button = new PushButton(icon, null);
        this.button.addActionListener(this);
        this.button.setHint(hint + (key == null ? "" : " ("+getKeyText(key)+")"));
        if (key != null) {
            bindKey(key);
        }
        
        addListener(this);
    }
    
    /**
     * Возвращает тип команды.
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
    
    @Override
    public final IButton getButton() {
        return button;
    }
    
    @Override
    public final void activate() {
        activator.accept(getContext());
    };
    
    /**
     * Добавляет слушатель событий команды.
     * @param listener Ссылка на слушатель.
     */
    public final void addListener(ICommandListener<V> listener) {
        listeners.add(listener);
    }

    @Override
    public final void setContext(List<V> context) {
        this.context.forEach((contextItem) -> {
            contextItem.model.removeModelListener(this);
        });
        this.context = context;
        listeners.forEach((listener) -> {
            listener.contextChanged(context);
        });
        this.context.forEach((contextItem) -> {
            contextItem.model.addModelListener(this);
        });
        activate();
    }
    
    public final void setContext(V context) {
        setContext(Arrays.asList(context));
    }
    
    /**
     * Предобработка модели параметров команды. Может использоваться для более
     * гибкой настройки редакторов параметров и их взаимосвязей.
     * @param contextItem Сущность контекста команды.
     * @param paramModel Модель набора параметров.
     */
    public void preprocessParameters(ParamModel paramModel) {
        // Do nothing
    }
    
    /**
     * Установка списка пераметров команды.
     * @param propHolders Список объектов {@link PropertyHolder} произвольной длины.
     */
    public EntityCommand setParameters(PropertyHolder... propHolders) {
        if (propHolders != null && propHolders.length > 0) {
            provider = () -> { return propHolders; };
        }
        return this;
    }
    
    /**
     * Получение вызов диалога заполнения параметров и возврат значений.
     */
    public final Map<String, IComplexType> getParameters() {
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
    public void actionPerformed(ActionEvent event) {
        Map<String, IComplexType> params = getParameters();
        if (params != null) {
            SwingUtilities.invokeLater(() -> {
                Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), getContext());
                getContext().forEach((entity) -> {
                    execute(entity, params);
                });
                activate();
            });
        }
    }
    
    @Override
    @Deprecated
    public final void execute(V context) {};
    
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
                    } catch (InterruptedException e) {}
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
    };

    @Override
    public void modelChanged(EntityModel model, List<String> changes) {
        activate();
    }

    @Override
    public void modelRestored(EntityModel model, List<String> changes) {
        activate();
    }

    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        activate();
    }

    @Override
    public final List<V> getContext() {
        return context == null ? null : new LinkedList(context);
    }
    
    /**
     * Привязка команды к комбинации клавиш клавиатуры.
     */
    private void bindKey(KeyStroke key) {
        InputMap inputMap = ((JComponent) this.button).getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (inputMap.get(key) != null && inputMap.get(key) == this) {
            // Do nothing
        } else if (inputMap.get(key) != null && inputMap.get(key) == this) {
            throw new IllegalStateException(MessageFormat.format(
                    "Key [{0}] already used by command ''{1}''", 
                    getKeyText(key), inputMap.get(key).getClass().getSimpleName()
            ));            
        } else {
            inputMap.put(key, this);
            ((JComponent) this.button).getActionMap().put(this, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (button.isEnabled()) {
                        EntityCommand.this.actionPerformed(event);
                    }
                }
            });
        }
    }
    
    /**
     * Возвращает строковое представление комбинации клавиш.
     */
    private String getKeyText(KeyStroke key) {
        if (key.getModifiers() != 0) {
            return KeyEvent.getKeyModifiersText(key.getModifiers())+"+"+KeyEvent.getKeyText(key.getKeyCode());
        } else {
            return KeyEvent.getKeyText(key.getKeyCode());
        }
    }

    @Override
    public void contextChanged(List<V> context) {
        // Do nothing
    };

    @Override
    public ImageIcon getIcon() {
        return (ImageIcon) button.getIcon();
    }
    
    @Override
    public String toString() {
        return IComplexType.coalesce(title, name);
    }
    
}
