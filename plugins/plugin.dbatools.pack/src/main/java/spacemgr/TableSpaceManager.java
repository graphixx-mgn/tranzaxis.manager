package spacemgr;

import codex.command.EditorCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.editor.AbstractEditor;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Common;
import manager.nodes.Database;
import plugin.Pluggable;
import plugin.command.CommandPlugin;
import spacemgr.command.objects.TableSpace;
import javax.swing.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.Map;

//@Pluggable.PluginOptions(provider = TableSpaceManager.Options.class)
public class TableSpaceManager extends CommandPlugin<Common> {

    private final static String FILTER = "^(RBS|RADIX).*$";

    public TableSpaceManager() {
        super(common -> true);
    }

    @Override
    public void execute(Common context, Map<String, IComplexType> params) {
        Database database = (Database) getOption(Options.OPT_DATABASE);
        String baseUri  = database.getDatabaseUrl(false);
        String userName = database.getDatabaseUser(false);
        String userPass = database.getDatabasePassword(false);

        Database tmpDb = createDBConnection(baseUri, userName, userPass);
        if (tmpDb.getConnectionID(true) != null) {
            ServiceRegistry.getInstance()
                    .lookupService(ITaskExecutorService.class)
                    .executeTask(new LoadTablespaces(tmpDb));
        }
    }

    private static Database createDBConnection(String url, String user, String password) {
        Database database = new Database(null, "SYS");
        database.setDatabaseUrl(url);
        database.setDatabaseUser(user);
        database.setDatabasePassword(password);

        Integer connectionId = database.getConnectionID(true);
        if (connectionId != null) {
            Logger.getLogger().debug(
                    "Created database connection:\n\turl:  {0}\n\tuser: {1}\n\tID:   {2}",
                    database.getDatabaseUrl(true),
                    database.getDatabaseUser(true),
                    database.getConnectionID(true)
            );
        }
        return database;
    }


    private final class LoadTablespaces extends AbstractTask<List<TableSpace>> {

        private final Database database;

        private LoadTablespaces(Database database) {
            super(Language.get(TableSpaceManager.class, "task@load"));
            this.database = database;
        }

        @Override
        public List<TableSpace> execute() throws Exception {
            List<String> tbsNames = new LinkedList<>();
            List<TableSpace> tableSpaces = new LinkedList<>();
            try (final ResultSet resultSet = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class).select(
                    database.getConnectionID(false),
                    "SELECT TABLESPACE_NAME FROM DBA_TABLESPACES ORDER BY TABLESPACE_NAME"
            )) {
                while (resultSet.next()) {
                    tbsNames.add(resultSet.getString("TABLESPACE_NAME"));
                }
            } catch (SQLException e) {
                Logger.getContextLogger(TableSpaceManager.class).error("Database error", e);
                throw e;
            }

            tbsNames.removeIf(name -> !name.matches(FILTER));
            tbsNames.removeIf(name -> name.endsWith("_TMP"));
            tbsNames.forEach(name -> {
                TableSpace tbs = Entity.newInstance(TableSpace.class, null, name);
                if (tbs != null) {
                    tbs.setDatabase(database);
                    tableSpaces.add(tbs);
                }
            });
            return tableSpaces;
        }

        @Override
        public void finished(List<TableSpace> result) {
            final ManagerWindow.TableSpaceView tbsView = new ManagerWindow.TableSpaceView();
            result.forEach(tbsView::attach);
            new Thread(() -> new ManagerWindow(tbsView).setVisible(true)).start();
        }
    }


    private enum UserStatus implements Iconified {
        @Enum.Undefined
        None(null),
        @Enum.Undefined
        Warn(ImageUtils.combine(
                ImageUtils.getByPath("/images/user.png"),
                ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), 0.6f),
                SwingConstants.SOUTH_EAST
        )),
        @Enum.Undefined
        Error(ImageUtils.combine(
                ImageUtils.grayscale(ImageUtils.getByPath("/images/user.png")),
                ImageUtils.getByPath("/images/unavailable.png")
        )),
        Success(ImageUtils.combine(
                ImageUtils.getByPath("/images/user.png"),
                ImageUtils.resize(ImageUtils.getByPath("/images/success.png"), 0.6f),
                SwingConstants.SOUTH_EAST
        ));


        private final ImageIcon icon;
        private final String pattern;
        UserStatus(ImageIcon icon) {
            this.icon = icon;
            this.pattern = Language.get(TableSpaceManager.class, "status@".concat(name().toLowerCase()));
        }

        private String getPattern() {
            return this.pattern;
        }

        @Override
        public ImageIcon getIcon() {
            return this.icon;
        }
    }


    public static class Options extends Pluggable.OptionsProvider {

        static final String OPT_DATABASE = "database";
        static final String OPT_USERSTAT = "status";
        static final String OPT_USERNAME = "user";

        private final ParamModel model = new ParamModel();
        {
            model.addProperty(new PropertyHolder<EntityRef<Database>, Database>(
                    OPT_DATABASE, getOptTitle(OPT_DATABASE), null,
                    new EntityRef<>(Database.class), true
            ) {
                @Override
                public boolean isValid() {
                    return super.isValid() && model.getProperty(OPT_USERSTAT).isValid();
                }
            });
            model.addProperty(new PropertyHolder<>(
                    OPT_USERSTAT, getOptTitle(OPT_USERSTAT), null,
                    new Enum<>(UserStatus.None, true), true)
            );
            model.addProperty(new PropertyHolder<>(
                    OPT_USERNAME, getOptTitle(OPT_USERNAME), null,
                    new AnyType(null) {
                        @Override
                        public Object getValue() {
                            final Database database = (Database) model.getValue(OPT_DATABASE);
                            return database == null ? null : new Iconified() {
                                final UserStatus status = (UserStatus) model.getValue(OPT_USERSTAT);

                                @Override
                                public ImageIcon getIcon() {
                                    return status.getIcon();
                                }

                                @Override
                                public String toString() {
                                    return MessageFormat.format(
                                            status.getPattern(),
                                            database.getDatabaseUser(false)
                                    );
                                }
                            };
                        }
                    }, false)
            );

            model.addChangeListener((name, oldValue, newValue) -> {
                if (OPT_DATABASE.equals(name)) {
                    model.setValue(OPT_USERSTAT, getUserStatus());
                    ((AbstractEditor) model.getEditor(OPT_USERNAME)).updateUI();
                }
            });
            model.setValue(OPT_USERSTAT, getUserStatus());
            model.getEditor(OPT_USERSTAT).setVisible(false);
        }

//        @Override
//        public ParamModel getOptions() {
//            return model;
//        }

        private String getOptTitle(String optName) {
            return Language.get(TableSpaceManager.class, optName.concat(PropertyHolder.PROP_NAME_SUFFIX));
        }

        private boolean checkRoleDBA(Database database) throws SQLException {
            final String query = Language.get(TableSpaceManager.class, "check.dba@query", Language.DEF_LOCALE);
            try (final ResultSet resultSet = ServiceRegistry.getInstance()
                    .lookupService(IDatabaseAccessService.class).select(
                            database.getConnectionID(true),
                            query
                    )
            ) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) == 1;
                }
                return false;
            }
        }

        private UserStatus getUserStatus() {
            Database db = (Database) model.getValue(OPT_DATABASE);
            if (db == null) return UserStatus.None;
            try {
                return checkRoleDBA(db) ? UserStatus.Success : UserStatus.Warn;
            } catch (SQLException e) {
                return UserStatus.Error;
            }
        }
    }
}
