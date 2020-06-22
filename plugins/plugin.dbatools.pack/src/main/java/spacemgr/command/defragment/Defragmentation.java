package spacemgr.command.defragment;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import spacemgr.command.objects.TableSpace;
import javax.swing.*;
import java.sql.SQLException;
import java.util.Map;

public class Defragmentation extends EntityCommand<TableSpace> {

    final static ImageIcon ICON_MAIN = ImageUtils.getByPath("/images/defrag.png");
    final static ImageIcon ICON_RUN  = ImageUtils.getByPath("/images/start.png");
    final static String   TITLE = Language.get(DefragmentationTask.class, "dialog@title");

    public Defragmentation() {
        super("tablespace defragmentation", TITLE, ICON_MAIN, TITLE, tableSpace -> tableSpace.getBlockSize() > 0);
    }

    @Override
    public void execute(TableSpace context, Map<String, IComplexType> params) {
        try {
            FormController controller = new FormController(context);
            CommandDialog dialog = new CommandDialog(controller);
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        } catch (SQLException e) {
            MessageBox.show(MessageType.ERROR, e.getMessage());
        }
    }
}
