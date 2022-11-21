package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import java.awt.*;
import java.io.IOException;
import java.util.Map;

public class OpenDir extends EntityCommand<Offshoot> {

    public OpenDir() {
        super(
                "open directory",
                Language.get("title"),
                ImageUtils.getByPath("/images/folder.png"),
                Language.get("title"),
                (offshoot) -> offshoot.getWCStatus() != WCStatus.Absent
        );
    }

    @Override
    public Kind getKind() {
        return Kind.Admin;
    }

    @Override
    public void execute(Offshoot context, Map<String, IComplexType> params) {
        try {
            Desktop.getDesktop().open(context.getLocalPath().toFile());
        } catch (IOException e) {
            MessageBox.show(MessageType.ERROR, e.getMessage());
        }
    }

    @Override
    public boolean disableWithContext() {
        return false;
    }
}