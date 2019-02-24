package codex.command;

import javax.swing.*;

public class CommandStatus {

    boolean   active;
    ImageIcon icon;

    public CommandStatus(boolean active) {
        this(active, null);
    }

    public CommandStatus(boolean active, ImageIcon icon) {
        this.active = active;
        this.icon   = icon;
    }

    public boolean isActive() {
        return active;
    }

}
