package codex.command;

import javax.swing.*;

/**
 * Класс для хранения расчитанного состояния команды {@link ICommand} при её активации, которое используется для
 * вызова событий:<br>
 * * {@link ICommandListener#commandIconChanged(ImageIcon)}<br>
 * * {@link ICommandListener#commandStatusChanged(boolean)}<br>
 * которые в свою очередь используются для информирования GUI о необходимости перерисовки кнопок запуска команд.
 */
public class CommandStatus {

    boolean   active;
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
        this.active = active;
        this.icon   = icon;
    }

    public boolean isActive() {
        return active;
    }

}
