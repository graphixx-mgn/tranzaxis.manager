package manager.commands.database;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Database;
import javax.swing.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Map;

public class CheckConnection extends EntityCommand<Database> {

    private static final ImageIcon COMMAND_ICON = ImageUtils.combine(
            ImageUtils.getByPath("/images/services.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/question.png"), .7f),
            SwingConstants.SOUTH_EAST
    );

    static {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException ignore) {}
    }

    public CheckConnection() {
        super(
                "check",
                Language.get(Database.class, "command@connect.check"),
                COMMAND_ICON,
                Language.get(Database.class, "command@connect.check"),
                null
        );
    }

    @Override
    public void execute(Database context, Map<String, IComplexType> params) {
        new Thread(() -> {
            Integer connectionID = context.getConnectionID(true);
            if (connectionID != null) {
                String url  = "jdbc:oracle:thin:@" + context.getDatabaseUrl(false);
                String user = context.getDatabaseUser(false);
                String pass = context.getDatabasePassword(false);

                try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                    MessageBox.show(MessageType.INFORMATION, MessageFormat.format(
                            Language.get(Database.class, "command@connect.success"),
                            context.getPID()
                    ));
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1017) {
                        MessageBox.show(MessageType.WARNING, Language.get(Database.class, "error@auth"));
                    } else {
                        MessageBox.show(MessageType.WARNING, e.getMessage());
                    }
                }
            }
        }).start();
    }
}
