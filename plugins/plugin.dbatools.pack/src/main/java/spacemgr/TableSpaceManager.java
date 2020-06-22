package spacemgr;

import codex.database.IDatabaseAccessService;
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
import codex.utils.Language;
import manager.nodes.Common;
import manager.nodes.Database;
import plugin.Pluggable;
import plugin.command.CommandPlugin;
import spacemgr.command.objects.TableSpace;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

@Pluggable.PluginOptions(provider = TableSpaceManager.Options.class)
public class TableSpaceManager extends CommandPlugin<Common> {

    private final static String PARAM_DB_URL  = "url";
    private final static String PARAM_DB_USER = "user";
    private final static String PARAM_DB_PASS = "password";

    private final static String FILTER = "^(RBS|RADIX).*$";

    public TableSpaceManager() {
        super(common -> true);
        setParameters(
                new PropertyHolder<>(PARAM_DB_URL, new EntityRef<>(Database.class), true),
                new PropertyHolder<>(PARAM_DB_USER, new Str("SYS AS SYSDBA"), true),
                new PropertyHolder<>(PARAM_DB_PASS, new Str("SYS"), true)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Common context, Map<String, IComplexType> params) {
        Database srcDb = ((EntityRef<Database>) params.get(PARAM_DB_URL)).getValue();
        Database tmpDb = createDBConnection(
                srcDb.getDatabaseUrl(false),
                ((Str) params.get(PARAM_DB_USER)).getValue(),
                ((Str) params.get(PARAM_DB_PASS)).getValue()
        );
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


    class Options extends Pluggable.OptionsProvider {

        private final ParamModel model = new ParamModel();
        {
            model.addProperty(new PropertyHolder<>("test", new Str("TEST"), true));
        }

        @Override
        public ParamModel getOptions() {
            return model;
        }
    }
}
