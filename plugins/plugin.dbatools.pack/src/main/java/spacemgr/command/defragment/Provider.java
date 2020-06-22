package spacemgr.command.defragment;

import codex.database.IDatabaseAccessService;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.utils.Language;
import manager.nodes.Database;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleDriver;
import spacemgr.TableSpaceManager;
import spacemgr.command.objects.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class Provider implements IFormController.IDataProvider {

    private final static Map<TableSpace, List<Extent>>       EXTENTS_CACHE = new HashMap<>();
    private final static Map<TableSpace, List<IProblematic>> PROBLEM_CACHE = new HashMap<>();
    private final static Map<TableSpace, List<Segment>>      SEGMENT_CACHE = new HashMap<>();

    private final IFormController controller;
    private final OracleConnection connection;
    private final List<IDataChangeListener> listeners = new LinkedList<>();

    Provider(IFormController controller) throws SQLException {
        EXTENTS_CACHE.putIfAbsent(controller.getTableSpace(), new LinkedList<>());
        PROBLEM_CACHE.putIfAbsent(controller.getTableSpace(), new LinkedList<>());
        SEGMENT_CACHE.putIfAbsent(controller.getTableSpace(), new LinkedList<>());
        this.controller = controller;
        this.connection = connect();
    }

    private OracleConnection connect() throws SQLException {
        final Database database = controller.getTableSpace().getDatabase();
        final String dbURL = MessageFormat.format("jdbc:oracle:thin:@//{0}", database.getDatabaseUrl(true));
        final String user  = database.getDatabaseUser(true);
        final String pass  = database.getDatabasePassword(true);

        OracleDriver driver = new OracleDriver();
        Properties prop = new Properties();
        prop.setProperty("user",     user);
        prop.setProperty("password", pass);
        return (OracleConnection) driver.connect(dbURL, prop);
    }

    @Override
    public void loadExtents() {
        final ITask loadStructure = new LoadExtents();
        ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).executeTask(loadStructure);
    }

    @Override
    public synchronized Collection<Extent> getExtents() {
        return EXTENTS_CACHE.get(controller.getTableSpace());
    }

    @Override
    public synchronized List<Segment> getSegments() {
        return new LinkedList<>(SEGMENT_CACHE.get(controller.getTableSpace()));
    }

    @Override
    public List<IProblematic> getProblems(Segment segment) {
        return PROBLEM_CACHE.get(controller.getTableSpace()).parallelStream()
                .filter(problematic -> problematic.isRelatedSegment(segment))
                .collect(Collectors.toList());
    }

    @Override
    public void addDataChangeListener(IDataChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeDataChangeListener(IDataChangeListener listener) {
        listeners.remove(listener);
    }

    private Segment getSegment(String owner, String type, String name, String part) {
        return SEGMENT_CACHE.get(controller.getTableSpace()).parallelStream()
                .filter(segment ->
                        Objects.equals(segment.getOwner(), owner) &&
                        Objects.equals(segment.getType(), type) &&
                        Objects.equals(segment.getName(), name) &&
                        Objects.equals(segment.getPart(), part)
                ).findFirst()
                .orElseGet(() -> {
                    Segment newSegment = new Segment(owner, type, name, part) {
                        @Override
                        public TableSpace getTableSpace() {
                            return controller.getTableSpace();
                        }

                        @Override
                        public Supplier<List<IProblematic>> problemsGetter() {
                            return () -> PROBLEM_CACHE.get(controller.getTableSpace()).parallelStream()
                                    .filter(problematic -> problematic.isRelatedSegment(this))
                                    .collect(Collectors.toList());
                        }
                    };
                    SEGMENT_CACHE.get(controller.getTableSpace()).add(newSegment);
                    return newSegment;
                });
    }

    private synchronized void insertExtent(Extent extent) {
        extent.getSegment().addExtent(extent);
        EXTENTS_CACHE.get(controller.getTableSpace()).add(extent);
        controller.getMapView().insertExtent(extent);
    }

    private synchronized void removeExtent(Extent extent) {
        extent.getSegment().delExtent(extent);
        EXTENTS_CACHE.get(controller.getTableSpace()).remove(extent);
        controller.getMapView().removeExtent(extent);
    }

    private synchronized void sortExtents() {
        EXTENTS_CACHE.get(controller.getTableSpace()).sort(
                Comparator.comparingLong(Extent::getFirstBlock)
        );
    }


    private class LoadExtents extends AbstractTask<List<Extent>> {

        private LoadExtents() {
            super(MessageFormat.format(
                    Language.get(Provider.class, "task@load"),
                    controller.getTableSpace().getPID()
            ));
        }

        @Override
        public List<Extent> execute() throws Exception {
            setProgress(0, Language.get(Provider.class, "load@check"));
            List<IProblematic> problematicObjects = detectProblematic();
            PROBLEM_CACHE.get(controller.getTableSpace()).clear();
            PROBLEM_CACHE.get(controller.getTableSpace()).addAll(problematicObjects);
            if (!problematicObjects.isEmpty()) {
                Logger.getContextLogger(TableSpaceManager.class).debug(
                        "Found problematic {0} objects:\n{1}",
                        problematicObjects.size(),
                        problematicObjects.stream()
                            .map(problematic -> "* "+problematic.getDescription(Locale.US))
                            .collect(Collectors.joining("\n"))
                );
            }

            int totalExtents  = 0;
            int totalSegments = 0;
            setProgress(0, Language.get(Provider.class, "load@count"));
            try (final ResultSet resultSet = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class).select(
                    controller.getTableSpace().getConnectionID(),
                    "SELECT SUM(EXTENTS) EXTENTS, COUNT(1) SEGMENTS FROM DBA_SEGMENTS WHERE TABLESPACE_NAME = ?",
                    controller.getTableSpace().getPID()
            )) {
                if (resultSet.next()) {
                    totalExtents  = resultSet.getInt("EXTENTS");
                    totalSegments = resultSet.getInt("SEGMENTS");
                }
            }
            Logger.getContextLogger(TableSpaceManager.class).debug(
                    "Total objects of tablespace ''{0}'': segments={1}, extents={2}",
                    controller.getTableSpace().getPID(),
                    String.valueOf(totalSegments),
                    String.valueOf(totalExtents)
            );

            Logger.getContextLogger(TableSpaceManager.class).debug(
                    "Load segments and extents of tablespace: {0}",
                    controller.getTableSpace().getPID()
            );
            setProgress(0, Language.get(Provider.class, "load@fetch"));
            final String query = MessageFormat.format(
                    Language.get(Provider.class, "load@query.sys", Locale.US),
                    controller.getTableSpace().getPID()
            );
            final PreparedStatement statement = connection.prepareStatement(
                    query,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY
            );

            statement.setFetchSize(Math.max(50, totalExtents / 250));
            Logger.getContextLogger(TableSpaceManager.class).debug(
                    "Set cursor fetch size: {0}",
                    statement.getFetchSize()
            );

            // Terminate SQL statement watchdog
            new Thread(() -> {
                while (!getStatus().isFinal()) {
                    if (isCancelled()) {
                        try {
                            statement.cancel();
                        } catch (SQLException ignore) {}
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignore) {}
                }
            }).start();

            ResultSet resultSet = null;
            try {
                resultSet = statement.executeQuery();
                int extentIdx = 0;

                Set<Integer> existingExtentHashes = getExtents().parallelStream()
                        .map(Extent::hashCode).collect(Collectors.toSet());
                Set<Integer> foundExtentHashes = new HashSet<>();
                while (resultSet.next()) {
                    final String owner = resultSet.getString("OWNER");
                    final String name  = resultSet.getString("SEGMENT_NAME");
                    final String type  = resultSet.getString("SEGMENT_TYPE");
                    final String part  = resultSet.getString("PARTITION_NAME");
                    final Long block   = resultSet.getLong(  "BLOCK_ID");
                    final Long size    = resultSet.getLong(  "BLOCKS");

                    SegmentType segmentType = SegmentType.byType(type);
                    if (segmentType == null) continue;
                    final Segment segment = getSegment(owner, type, name, part);

                    extentIdx++;
                    int percent = 100 * extentIdx / totalExtents;
                    setProgress(percent, getDescription());

                    final Extent extent = new Extent(owner, name, type, part, block, size) {
                        @Override
                        public Segment getSegment() {
                            return segment;
                        }
                    };
                    foundExtentHashes.add(extent.hashCode());
                    if (!existingExtentHashes.contains(extent.hashCode())) {
                        existingExtentHashes.add(extent.hashCode());
                        insertExtent(extent);
                    }
                }
                // Synchronize
                Logger.getContextLogger(TableSpaceManager.class).debug("Delete obsolete extents");
                new LinkedList<>(getExtents()).parallelStream().forEach(extent -> {
                    if (!foundExtentHashes.contains(extent.hashCode())) {
                        removeExtent(extent);
                    }
                });
                Logger.getContextLogger(TableSpaceManager.class).debug("Sorting loaded extents");
                sortExtents();

                Logger.getContextLogger(TableSpaceManager.class).debug(
                        "Loaded objects of tablespace ''{0}'': segments={1}, extents={2}",
                        controller.getTableSpace().getPID(),
                        String.valueOf(getSegments().size()),
                        String.valueOf(getExtents().size())
                );
                new LinkedList<>(listeners).forEach(IDataChangeListener::dataLoaded);
            } catch (SQLException e) {
                if (e.getErrorCode() != 1013) throw e;
            } finally {
                if (resultSet != null) {
                    resultSet.getStatement().close();
                    resultSet.close();
                }
            }
            return null;
        }

        List<IProblematic> detectProblematic() {
            Logger.getContextLogger(TableSpaceManager.class).debug(Language.get(Provider.class, "load@check", Locale.US));
            List<IProblematic> problematicObjects = new LinkedList<>();
            try {
                final PreparedStatement statement = connection.prepareStatement(
                        Language.get(Provider.class, "load@problem.long", Locale.US),
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY
                );
                statement.setString(1, controller.getTableSpace().getPID());
                ResultSet resultSet = null;
                try {
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        final String owner  = resultSet.getString("OWNER");
                        final String table  = resultSet.getString("TABLE_NAME");
                        final String column = resultSet.getString("COLUMN_NAME");
                        problematicObjects.add(new InvalidColumnDatatype(owner, table, column));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    if (resultSet != null) {
                        resultSet.getStatement().close();
                        resultSet.close();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return problematicObjects;
        }

        @Override
        public void finished(List<Extent> result) {}
    }


    class InvalidColumnDatatype implements IProblematic {

        private final String owner, table, column;

        InvalidColumnDatatype(String owner, String table, String column) {
            this.owner  = owner;
            this.table  = table;
            this.column = column;
        }

        @Override
        public boolean isRelatedSegment(Segment segment) {
            return  SegmentType.TABLE.equals(SegmentType.byType(segment.getType())) &&
                    owner.equals(segment.getOwner()) &&
                    table.equals(segment.getName());
        }

        @Override
        public String getDescription(Locale locale) {
            return MessageFormat.format(
                    Language.get(Provider.class, "desc@problem.long", locale),
                    owner, table, column
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InvalidColumnDatatype that = (InvalidColumnDatatype) o;
            return Objects.equals(owner, that.owner) &&
                   Objects.equals(table, that.table) &&
                   Objects.equals(column, that.column);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, table, column);
        }
    }
}
