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
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Common;
import manager.nodes.Database;
import plugin.Pluggable;
import plugin.command.CommandPlugin;
import spacemgr.command.objects.TableSpace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

@Pluggable.PluginOptions(provider = TableSpaceManager.Options.class)
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


    public class Options extends Pluggable.OptionsProvider {

        static final String OPT_DATABASE = "database";
        static final String OPT_USERNAME = "user";
        static final String OPT_USERPASS = "password";

        private final ParamModel model = new ParamModel();
        {
            model.addProperty(new PropertyHolder<>(OPT_DATABASE, getOptTitle(OPT_DATABASE), null, new EntityRef<>(Database.class), true));
            model.addProperty(new PropertyHolder<>(OPT_USERNAME, getOptTitle(OPT_USERNAME), null, new Str(null) {
                @Override
                public String getValue() {
                    Database db = (Database) model.getValue(OPT_DATABASE);
                    return db == null ? null : db.getDatabaseUser(false);
                }
            }, true));
            model.addProperty(new PropertyHolder<>(OPT_USERPASS, getOptTitle(OPT_USERPASS), null, new Str(null) {
                @Override
                public String getValue() {
                    Database db = (Database) model.getValue(OPT_DATABASE);
                    return db == null ? null : db.getDatabasePassword(false);
                }
            }, true));

            model.addChangeListener((name, oldValue, newValue) -> {
                if (name.equals(OPT_DATABASE)) {
                    ((AbstractEditor) model.getEditor(OPT_USERNAME)).updateUI();
                    ((AbstractEditor) model.getEditor(OPT_USERPASS)).updateUI();
                }
            });
            //noinspection unchecked
            model.getEditor(OPT_DATABASE).addCommand(new CheckDBA());
            model.getEditor(OPT_USERNAME).setEditable(false);
            model.getEditor(OPT_USERPASS).setEditable(false);
        }

        @Override
        public ParamModel getOptions() {
            return model;
        }

        private String getOptTitle(String optName) {
            return Language.get(TableSpaceManager.class, optName.concat(PropertyHolder.PROP_NAME_SUFFIX));
        }


        private class CheckDBA extends EditorCommand<EntityRef<Database>, Database> {

            private CheckDBA() {
                super(
                        ImageUtils.resize(ImageUtils.getByPath("/images/question.png"), 19, 19),
                        Language.get(TableSpaceManager.class, "check.dba@title"),
                        PropertyHolder::isValid
                );
            }

            @Override
            public void execute(PropertyHolder<EntityRef<Database>, Database> context) {
                final String query = Language.get(TableSpaceManager.class, "check.dba@query", Locale.US);
                try (final ResultSet resultSet = ServiceRegistry.getInstance()
                            .lookupService(IDatabaseAccessService.class).select(
                                    context.getPropValue().getValue().getConnectionID(true),
                                    query
                            )
                ) {
                    if (resultSet.next()) {
                        if (resultSet.getInt(1) == 1) {
                            MessageBox.show(
                                    MessageType.INFORMATION,
                                    Language.get(TableSpaceManager.class, "check.dba@success")
                            );
                        } else {
                            MessageBox.show(
                                    MessageType.ERROR,
                                    Language.get(TableSpaceManager.class, "check.dba@fail")
                            );
                        }
                    }
                } catch (SQLException e) {
                    MessageBox.show(MessageType.ERROR, MessageFormat.format(
                            Language.get(TableSpaceManager.class, "check.dba@error"),
                            e.getLocalizedMessage()
                    ));
                }
            }
        }
    }
}
