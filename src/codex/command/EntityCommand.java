package codex.command;

import codex.component.button.IButton;
import codex.component.button.PushButton;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.presentation.CommitEntity;
import codex.presentation.RollbackEntity;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import codex.type.Iconified;
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
 * В частности, таким образом реализуется сохранение и откат изменений
 * @see CommitEntity
 * @see RollbackEntity
 */
public abstract class EntityCommand implements ICommand<Entity>, ActionListener, IModelListener, ICommandListener<Entity>, Iconified {
    
    private KeyStroke key;
    private String    name;
    private String    title;
    private Entity[]  context;
    private IButton   button; 
    private final List<ICommandListener<Entity>> listeners = new LinkedList<>();
    private Supplier<PropertyHolder[]> provider = () -> { return new PropertyHolder[] {}; };
    
    protected Predicate<Entity>  available;
    protected Consumer<Entity[]> activator = (entities) -> {
        button.setEnabled(
                entities != null && entities.length > 0 && 
                !(entities.length > 1 && !multiContextAllowed()) && (
                        available == null || Arrays.asList(entities).stream().allMatch(available)
                )
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
    public EntityCommand(String name, String title, ImageIcon icon, String hint, Predicate<Entity> available) {
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
    public EntityCommand(String name, String title, ImageIcon icon, String hint, Predicate<Entity> available, KeyStroke key) {
        if (icon == null) {
            throw new IllegalStateException("Parameter 'icon' can not be NULL");
        }
        this.key       = key;
        this.name      = name;
        this.title     = title;
        this.available = available;
        
        this.button = new PushButton(icon, null);
        this.button.addActionListener(this);
        this.button.setHint(hint + (key == null ? "" : " ("+getKeyText(key)+")"));
        
        addListener(this);
    }
    
    public final String getName() {
        return name;
    }
    
    @Override
    public final IButton getButton() {
        if (key != null) {
            SwingUtilities.invokeLater(() -> {
                    bindKey(key);
            });
        }
        return button;
    }
    
    @Override
    public final void activate() {
        activator.accept(getContext());
    };
    
    public final void addListener(ICommandListener<Entity> listener) {
        listeners.add(listener);
    }

    @Override
    public final void setContext(Entity... context) {
        if (this.context != null) {
            Arrays.asList(this.context).forEach((entity) -> {
                entity.model.removeModelListener(this);
            });
        }
        this.context = context;
        listeners.forEach((listener) -> {
            listener.contextChanged(context);
        });
        if (this.context != null) {
            Arrays.asList(this.context).forEach((entity) -> {
                entity.model.addModelListener(this);
            });
        }
        activate();
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

    
    @Override
    public void actionPerformed(ActionEvent event) {
        Map<String, IComplexType> params = getParameters();
        if (params != null) {
            SwingUtilities.invokeLater(() -> {
                Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), Arrays.asList(getContext()));
                for (Entity entity : getContext()) {
                    execute(entity, params);
                }
                activate();
            });
        }
    }
    
    public final Map<String, IComplexType> getParameters() {
        ParametersDialog paramDialog = new ParametersDialog(this, provider);
        try {
            return paramDialog.call();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    @Override
    @Deprecated
    public final void execute(Entity context) {};
    
    public abstract void execute(Entity context, Map<String, IComplexType> params);

    @Override
    public void modelChanged(EntityModel model, List<String> changes) {
        activate();
    }

    @Override
    public final Entity[] getContext() {
        return context;
    }
    
    /**
     * Привязка команды к комбинации клавиш клавиатуры.
     */
    private void bindKey(KeyStroke key) {
        InputMap inputMap = ((JComponent) ((JComponent) this.button).getParent()).getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (inputMap.get(key) != null && inputMap.get(key) != this) {
            throw new IllegalStateException(MessageFormat.format(
                    "Key [{0}] already used by command ''{1}''", 
                    getKeyText(key), inputMap.get(key).getClass().getSimpleName()
            ));
        } else {
            inputMap.put(key, this);
            ((JComponent) ((JComponent) this.button).getParent()).getActionMap().put(this, new AbstractAction() {
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
    public void contextChanged(Entity... context) {
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
