package manager.nodes;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.mask.RegexMask;
import codex.model.*;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import codex.utils.NetTools;
import javax.swing.*;

public class Database extends Entity {
    private static final IDatabaseAccessService OAS = OracleAccessService.getInstance();
    private final static Integer CONNECT_TIMEOUT = 1000;
    
    private final static String  PROP_BASE_URL  = "dbUrl";
    private final static String  PROP_BASE_USER = "dbSchema";
    private final static String  PROP_BASE_PASS = "dbPass";
    private final static String  PROP_USER_NOTE = "userNote";
    public  final static String  PROP_CONN_LOCK = "lock";
    public  final static String  PROP_CONN_STAT = "status";

    private final static Pattern   URL_SPLITTER = Pattern.compile("([\\d\\.]+|[^\\s]+):(\\d+)/");
    private final static ImageIcon ICON_ONLINE  = ImageUtils.getByPath("/images/database.png");
    private final static ImageIcon ICON_OFFLINE = ImageUtils.combine(
            ICON_ONLINE,
            ImageUtils.resize(ImageUtils.getByPath("/images/stop.png"), .6f),
            SwingConstants.SOUTH_EAST
    );
    private final static ImageIcon ICON_UNKNOWN = ImageUtils.combine(
            ICON_ONLINE,
            ImageUtils.resize(ImageUtils.getByPath("/images/update.png"), .6f),
            SwingConstants.SOUTH_EAST
    );
    
    static {
        ServiceRegistry.getInstance().registerService(OAS);
        CommandRegistry.getInstance().registerCommand(CheckConnection.class);
    }

    private final PropertyHolder<Bool, Boolean> checked = new PropertyHolder<>(
            PROP_CONN_LOCK,
            new Bool(false),
            false
    );
    
    public Database(EntityRef owner, String title) {
        super(owner, ICON_ONLINE, title, null);

        // Properties
        model.addUserProp(PROP_BASE_URL, 
                new Str(null).setMask(new RegexMask(
                        "((([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))|[^\\s]+):"+
                        "(6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[1-9][0-9]{1,3}|[0-9])"+
                        "/\\w+", 
                        Language.get("dbUrl.error")
                )),
        true, Access.Select);
        model.addUserProp(PROP_BASE_USER, new Str(null), true, null);
        model.addUserProp(PROP_BASE_PASS, new Str(null), true, Access.Select);
        model.addUserProp(PROP_USER_NOTE, new Str(null), false, null);

        model.addDynamicProp(PROP_CONN_STAT, new Enum<>(Status.Unknown), Access.Any, null);

        // Handlers
        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                onChange(changes);
            }

            @Override
            public void modelRestored(EntityModel model, List<String> changes) {
                onChange(changes);
            }

            private void onChange(List<String> changes) {
                if (changes.contains(PROP_BASE_URL)) {
                    checkConnection(false);
                }
            }
        });
        model.addChangeListener((name, oldValue, newValue) -> {
            if (name.equals(PROP_CONN_STAT)) {
                setIcon(((Status) newValue).getIcon());
            }
        });
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        checkConnection(false);
    }
    
    public final String getDatabaseUrl(boolean unsaved) {
        return (String) (unsaved? model.getUnsavedValue(PROP_BASE_URL) : model.getValue(PROP_BASE_URL));
    }
    
    public final String getDatabaseUser(boolean unsaved) {
        return (String) (unsaved? model.getUnsavedValue(PROP_BASE_USER) : model.getValue(PROP_BASE_USER));
    }
    
    public final String getDatabasePassword(boolean unsaved) {
        return (String) (unsaved? model.getUnsavedValue(PROP_BASE_PASS) : model.getValue(PROP_BASE_PASS));
    }
    
    public Integer getConnectionID(boolean showError) {
        String url  = getDatabaseUrl(true);
        String user = getDatabaseUser(true);
        String pass = getDatabasePassword(true);

        if (IComplexType.notNull(url, user, pass)) {
            synchronized (checked) {
                if (checked.getPropValue().getValue()) {
                    return null;
                }
                Status status = checkUrlPort(url) ? Status.Online : Status.Offline;
                SwingUtilities.invokeLater(() -> model.setValue(PROP_CONN_STAT, status));
                if (status == Status.Offline) {
                    checked.getPropValue().setValue(true);
                    if (showError) {
                        MessageBox.show(MessageType.WARNING, MessageFormat.format(
                                Language.get(Database.class, "error@unavailable"),
                                url.substring(0, url.indexOf("/"))
                        ));
                    }
                    return null;
                }
            }
            try {
                return Database.OAS.registerConnection("jdbc:oracle:thin:@//"+url, user, pass);
            } catch (SQLException e) {
                if (showError) {
                    MessageBox.show(MessageType.ERROR, e.getMessage());
                } else {
                    Logger.getLogger().warn(
                            "Unable to open connection for database ''{0}'': {1}",
                            this, e.getMessage()
                    );
                }
            }
        } else {
            if (showError) {
                MessageBox.show(
                        MessageType.WARNING,
                        Language.get(Database.class, "error@notready")
                );
            } else {
                Logger.getLogger().warn(
                        Language.get(Database.class, "error@notready", Language.DEF_LOCALE)
                );
            }
        }
        return null;
    }

    public boolean isConnected() {
        return model.getValue(PROP_CONN_STAT) == Status.Online;
    }

    private void checkConnection(boolean showError) {
        new Thread(() -> {
            checked.getPropValue().setValue(false);
            String dbUrl = getDatabaseUrl(true);
            if (getConnectionID(showError) == null) {
                Logger.getLogger().warn(
                        "Database ''{0}'' address ({1}) is not available",
                        getPID(), dbUrl.substring(0, dbUrl.indexOf("/"))
                );
            }
        }).start();
    }

    private synchronized boolean checkUrlPort(String dbUrl) {
        Matcher verMatcher = URL_SPLITTER.matcher(dbUrl);
        if (verMatcher.find()) {
            String host = verMatcher.group(1);
            int    port = Integer.parseInt(verMatcher.group(2));
            try {
                return NetTools.isPortAvailable(host, port, CONNECT_TIMEOUT);
            } catch (IllegalStateException e) {
                return false;
            }
        } else {
            return false;
        }
    }


    private static class CheckConnection extends EntityCommand<Database> {
        private static final ImageIcon COMMAND_ICON = ImageUtils.combine(
                ImageUtils.getByPath("/images/services.png"),
                ImageUtils.resize(ImageUtils.getByPath("/images/question.png"), .8f),
                SwingConstants.SOUTH_EAST
        );

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
        public Kind getKind() {
            return Kind.Admin;
        }

        @Override
        public void execute(Database context, Map<String, IComplexType> params) {
            Integer connectionID;
            synchronized (context.checked) {
                context.checkConnection(true);
                //context.checked.getPropValue().setValue(false);
                connectionID = context.getConnectionID(true);
            }
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
        }
    }


    public enum Status implements Iconified {
        Unknown (ICON_UNKNOWN),
        Online  (ICON_ONLINE),
        Offline (ICON_OFFLINE);

        private final ImageIcon icon;
        Status(ImageIcon icon) {
            this.icon = icon;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }
    }
    
}
