package codex.command;

import javax.swing.*;

/**
 * Класс для хранения и передачи расчитанного состояния команд {@link ICommand} при её активации.
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
     * @param icon Иконка кнопки команды на панели инструментов должна быть изменена на указанную.
     */
    public CommandStatus(boolean active, ImageIcon icon) {
        this.active = active;
        this.icon   = icon;
    }

    public boolean isActive() {
        return active;
    }

}
