package plugin;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.type.IComplexType;
import manager.nodes.Common;
import plugin.command.CommandPlugin;
import java.util.Map;

public class CommandTemplate extends CommandPlugin<Common> {

    public CommandTemplate() {
        super(common -> true);
    }

    @Override
    public void execute(Common context, Map<String, IComplexType> params) {
        MessageBox.show(MessageType.INFORMATION, "It's a command plugin template");
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }
}