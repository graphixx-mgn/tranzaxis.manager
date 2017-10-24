package codex.command;

import codex.component.button.IButton;
import codex.component.button.PushButton;
import codex.log.Logger;
import codex.model.Entity;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.ImageIcon;
import codex.model.IModelListener;
import codex.presentation.CommitEntity;
import codex.presentation.RollbackEntity;

/**
 * Абстрактная реализация команд сущности {@link Entity}.
 * Используется для возможности производить различные действия над сущностью.
 * В частности, таким образом реализуется сохранение и откат изменений
 * @see CommitEntity
 * @see RollbackEntity
 */
public abstract class EntityCommand implements ICommand<Entity>, ActionListener, IModelListener {
    
    private   String   name;
    private   Entity[] context;
    protected IButton  button;
    protected Predicate<Entity>  available;
    protected Consumer<Entity[]> activator = (entities) -> {
        button.setEnabled(
                entities != null && entities.length > 0 && (
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
        if (icon == null) {
            throw new IllegalStateException("Parameter 'icon' can not be NULL");
        }
        this.name      = name;
        this.available = available;
        
        this.button = new PushButton(icon, title);
        this.button.addActionListener(this);
        this.button.setHint(hint);
        
        activator.accept(getContext());
    }
    
    @Override
    public IButton getButton() {
        return button;
    }

    @Override
    public void setContext(Entity... context) {
        if (this.context != null) {
            Arrays.asList(this.context).forEach((entity) -> {
                entity.model.removeModelListener(this);
            });
        }
        this.context = context;
        if (this.context != null) {
            Arrays.asList(this.context).forEach((entity) -> {
                entity.model.addModelListener(this);
            });
        }
        activator.accept(getContext());
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        for (Entity entity : context) {
            Logger.getLogger().debug("Perform command [{0}]. Context: {1}", new Object[]{name, entity});
            execute(entity);
        }
        activator.accept(getContext());
    }

    @Override
    public void modelChanged(List<String> changes) {
        activator.accept(getContext());
    }

    @Override
    public final Entity[] getContext() {
        return context;
    }
    
}
