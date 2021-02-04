package codex.command;

import net.jcip.annotations.ThreadSafe;
import javax.swing.*;

/**
 * Класс для хранения расчитанного состояния команды {@link ICommand} при её активации, которое используется для
 * вызова событий:<br>
 * * {@link ICommandListener#commandIconChanged(ImageIcon)}<br>
 * * {@link ICommandListener#commandStatusChanged(boolean, Boolean)}<br>
 * которые в свою очередь используются для информирования GUI о необходимости перерисовки кнопок запуска команд.
 */
@ThreadSafe
public class CommandStatus {

    boolean   active;
    Boolean   hidden;
    ImageIcon icon;

    /**
     * Конcтруктор.
     * @param active Команда доступна для исполнения для текущего контекста.
     */
    public CommandStatus(boolean active) {
        this(active, null);
    }

    /**
     * Конcтруктор.
     * @param active Команда доступна для исполнения для текущего контекста.
     * @param icon Иконка кнопки команды должна быть изменена на указанную.
     */
    public CommandStatus(boolean active, ImageIcon icon) {
        this(active, icon, null);
    }

    /**
     * Конcтруктор.
     * @param active Команда доступна для исполнения для текущего контекста.
     * @param icon Иконка кнопки команды должна быть изменена на указанную.
     * @param hidden Переключить видимость кнопки команды (если не NULL).
     */
    public CommandStatus(boolean active, ImageIcon icon, Boolean hidden) {
        this.active = active;
        this.icon   = icon;
        this.hidden = hidden;
    }

    public boolean isActive() {
        return active;
    }

}
