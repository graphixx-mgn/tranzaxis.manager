package spacemgr.command.defragment;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.context.IContext;
import codex.database.IDatabaseAccessService;
import codex.log.Level;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.utils.FileUtils;
import codex.utils.Language;
import org.apache.commons.io.FilenameUtils;
import spacemgr.TableSpaceManager;
import spacemgr.command.objects.*;
import javax.swing.*;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DefragmentationTask extends AbstractTask<Void> {

    private static final ThreadLocal<IFormController.IDataProvider> threadLocalScope = new  ThreadLocal<>();
    private static IFormController.IDataProvider getProvider() {
        return threadLocalScope.get();
    }
    private static void setProvider(IFormController.IDataProvider provider) {
        threadLocalScope.set(provider);
    }

    private final static Integer DDL_LOCK_TIMEOUT = 30; // Seconds
    private final static String  COMMAND_SUCCESS  = Language.get("step@process.success");
    private final static String  COMMAND_SKIP     = Language.get("step@process.skip");
    private final static String  COMMAND_ERROR    = Language.get("step@process.fail");

    private final IFormController  controller;
    private final Supplier<String> spaceName;
    private final List<Segment>    segments;

    // Контексты логирования
    @LoggingSource()
    @IContext.Definition(
            id     = "Sql.Gen",
            name   = "SQL Command generator",
            icon   = "/images/sql_generator.png",
            parent = TableSpaceManager.class
    )
    private static class SQLGenerator implements IContext {}


    DefragmentationTask(IFormController controller, List<Segment> segments) {
        super(
                segments == null ?  MessageFormat.format(
                        Language.get("title@space"),
                        controller.getTableSpace().getPID()
                ) : Language.get("title@segments")
        );
        this.controller = controller;
        this.spaceName  = () -> controller.getTableSpace().getPID();
        this.segments   = segments;

        Entity cellView = (Entity) controller.getCellView();
        ITaskListener lockHandler = new ITaskListener() {
            @Override
            public void beforeExecute(ITask task) {
                if (cellView != null && !cellView.islocked()) {
                    try {
                        cellView.getLock().acquire();
                    } catch (InterruptedException e) {
                        //
                    }
                }
            }

            @Override
            public void afterExecute(ITask task) {
                if (cellView != null) {
                    cellView.getLock().release();
                }
            }
        };
        addListener(lockHandler);
    }

    @Override
    public boolean isPauseable() {
        return true;
    }

    @Override
    public Void execute() throws Exception {
        controller.initLogOutput(this);
        
        List<Segment> processSegments;
        if (segments == null || segments.isEmpty()) {
            // Get tablespace extent information
            long usedBlocks = getUsedBlocks();
            double usedPercent = getUsedPercent();
            long startBlock = getStartBlock();

            // Print tablespace extent information
            TaskOutput.put(
                    Level.Info,
                    MessageFormat.format(
                            Language.get("step@info"),
                            spaceName.get(),
                            String.valueOf(controller.getTableSpace().getBlockSize()),
                            String.valueOf(controller.getDataProvider().getExtents().size()),
                            String.valueOf(controller.getTableSpace().getBlocks()),
                            String.valueOf(usedBlocks),
                            String.format("%.2f", usedPercent),
                            FileUtils.formatFileSize(usedBlocks * controller.getTableSpace().getBlockSize()),
                            FileUtils.formatFileSize((controller.getTableSpace().getBlocks() - usedBlocks) * controller.getTableSpace().getBlockSize())
                    ).replaceAll("\n", "<br>")
            );
            // Filter extents to move
            processSegments = prepareSegments(startBlock);
        } else {
            processSegments = new LinkedList<>(segments);
        }

        // Prepare commands
        setProvider(controller.getDataProvider());
        List<Command> processCommands = prepareCommands(processSegments, false);

        // Process commands
        if (!processCommands.isEmpty()) {
            initializeProcess();
            process(processCommands);
            finalizeProcess();
        }
        return null;
    }

    private long getUsedBlocks() {
        return controller.getDataProvider().getExtents().parallelStream()
                .mapToLong(Extent::getSize)
                .sum();
    }

    private long getFreeBlocks() {
        return controller.getTableSpace().getBlocks() - getUsedBlocks();
    }

    private double getUsedPercent() {
        return new BigDecimal((double) getUsedBlocks() * 100 / controller.getTableSpace().getBlocks())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private long getStartBlock() {
        return 128 + getUsedBlocks() + 5 * getFreeBlocks() / 100;
    }

    private List<Segment> prepareSegments(long startBlock) {
        TaskOutput.put(Level.Info, Language.get("step@prepare"));
        setProgress(0, Language.get("step@prepare"));

        Logger.getContextLogger(TableSpaceManager.class).debug(
                "Filter extents before start block (ID: {0})",
                String.valueOf(startBlock)
        );
        java.util.List<Extent> extents = controller.getDataProvider().getExtents().stream()
                .filter(extent -> extent.getLastBlock() >= startBlock)
                .distinct()
                .collect(Collectors.toList());

        // Drop unknown and not operable segment types
        Map<String, Segment> unsupported = new HashMap<>();
        Map<String, Segment> unknown     = new HashMap<>();

        extents.removeIf(extent -> {
            SegmentType type = SegmentType.byType(extent.getType());
            if (type == null) {
                unknown.putIfAbsent(extent.getType(), extent.getSegment());
                return true;
            }
            switch (type) {
                case TABLE:
                case TABLE_PARTITION:
                case TABLE_SUBPARTITION:
                case LOB:
                case LOB_PARTITION:
                case LOB_SUBPARTITION:
                case INDEX:
                case LOBINDEX:
                case INDEX_PARTITION:
                case INDEX_SUBPARTITION:
                {
                    return false;
                }
                default:
                    unsupported.putIfAbsent(extent.getType(), extent.getSegment());
                    return true;
            }
        });

        if (!unknown.isEmpty()) {
            Logger.getContextLogger(TableSpaceManager.class).warn(
                    "Unknown segment types:\n{0}",
                    unknown.values().stream()
                            .map(Segment::toString)
                            .collect(Collectors.joining("\n"))
            );
        }
        if (!unsupported.isEmpty()) {
            Logger.getContextLogger(TableSpaceManager.class).warn(
                    "Unsupported segment types:\n{0}",
                    unsupported.values().stream()
                            .map(Segment::toString)
                            .collect(Collectors.joining("\n"))
            );
        }

        Collections.reverse(extents);
        List<Segment> segments = extents.parallelStream().map(Extent::getSegment).distinct().collect(Collectors.toList());


        if (!segments.isEmpty()) {
            Logger.getContextLogger(TableSpaceManager.class).debug(
                    "Found {0} segments:\n{1}",
                    String.valueOf(segments.size()),
                    segments.stream()
                            .map(segment -> MessageFormat.format("* {0}", segment))
                            .collect(Collectors.joining("\n"))
            );
        }
        TaskOutput.put(Level.Info,
                Language.get("step@prepare.result"),
                String.valueOf(segments.size()),
                String.valueOf(extents.size()),
                FileUtils.formatFileSize(segments.parallelStream().mapToLong(Segment::getSize).sum())
        );
        return segments;
    }

    private List<Command> prepareCommands(List<Segment> preparedSegments, boolean repair) {
        if (preparedSegments.isEmpty()) {
            TaskOutput.put(Level.Info, Language.get("result@skip"));
            return Collections.emptyList();

        } else {
            if (!repair) {
                TaskOutput.put(Level.Info, Language.get("step@generate"));
                setProgress(0, Language.get("step@generate"));
            }

            List<Command> commands = new LinkedList<>();
            List<Command> accessCommands = generateGrantCommands(preparedSegments);
            List<Command> moveCommands   = generateMoveCommands(preparedSegments);

            commands.addAll(accessCommands);
            commands.addAll(moveCommands);

            long totalCommands = commandsCount(commands);
            if (!repair) {
                TaskOutput.put(Level.Info, Language.get("step@generate.result"), String.valueOf(totalCommands));
            }

            if (!commands.isEmpty()) {
                final String pattern = repair ? "Prepared commands for repair:\n{0}" : "Prepared commands for movement:\n{0}";
                Logger.getContextLogger(SQLGenerator.class).debug(
                        pattern,
                        commands.stream()
                                .map(command -> command.getSQL(Direction.Backward))
                                .collect(Collectors.joining("\n"))
                );
            }

            if (!repair && commands.isEmpty()) {
                TaskOutput.put(Level.Info, Language.get("result@skip"));
                return Collections.emptyList();

            } else if (!repair) {
                // Confirmation
                AtomicBoolean confirmed = new AtomicBoolean(false);
                try {
                    Semaphore lock = new Semaphore(1);
                    lock.acquire();
                    SwingUtilities.invokeLater(() -> {
                        confirmed.set(MessageBox.confirmation(
                                MessageType.CONFIRMATION.toString(),
                                MessageFormat.format(
                                        Language.get("step@confirm"),
                                        totalCommands
                                )
                        ));
                        lock.release();
                    });
                    try { lock.acquire(); } finally { lock.release(); }
                } catch (Exception ignore) {}

                if (!confirmed.get()) {
                    TaskOutput.put(Level.Warn, Language.get("result@canceled"));
                    Logger.getContextLogger(TableSpaceManager.class).info("Operation was canceled by user");
                    throw new CancelException();
                }
            }
            return commands;
        }
    }

    private long commandsCount(List<Command> commands) {
        return Math.addExact(
                commands.parallelStream().filter(Command::isForward).count(),
                commands.parallelStream().filter(Command::isBackward).count()
        );
    }

    private List<Command> generateGrantCommands(List<Segment> preparedSegments) {
        List<Command> result = new LinkedList<>();
        List<String>  users  = preparedSegments.parallelStream()
                .map(Segment::getOwner)
                .distinct()
                .collect(Collectors.toList());
        Logger.getContextLogger(SQLGenerator.class).debug(
                "Generate access grant commands for users:\n{0}",
                users.stream()
                        .map(user -> MessageFormat.format("* {0}", user))
                        .collect(Collectors.joining("\n"))
        );
        users.forEach(user -> result.add(new Quota(spaceName.get(), user)));
        return result;
    }

    private List<Command> generateMoveCommands(List<Segment> preparedSegments) {
        List<Command> commands = new LinkedList<>();

        // Generate table move commands
        Logger.getContextLogger(SQLGenerator.class).debug("Generate table move commands");
        preparedSegments.forEach(segment -> {
            SegmentType type = SegmentType.byType(segment.getType());
            if (type != null) {
                switch (type) {
                    case TABLE:
                    case TABLE_PARTITION:
                    case TABLE_SUBPARTITION: {
                        commands.add(new Table(segment));
                        break;
                    }
                }
            }
       });

        // Generate lob objects move commands
        Logger.getContextLogger(SQLGenerator.class).debug("Generate LOB move commands");
        preparedSegments.forEach(segment -> {
            SegmentType type = SegmentType.byType(segment.getType());
            if (type != null) {
                switch (type) {
                    case LOB:
                    case LOB_PARTITION:
                    case LOB_SUBPARTITION: {
                        Lob lobCommand = Lob.getLob(segment);
                        if (lobCommand != null) {
                            lobCommand.findDependantSegments()
                                    .forEach(lobCommand::addDependency);
                            commands.add(lobCommand);
                        }
                        break;
                    }
                }
            }
        });

        // Generate index move commands
        Map<String, String> indexType = new HashMap<>();
        Logger.getContextLogger(SQLGenerator.class).debug("Generate index move commands");
        preparedSegments.forEach(segment -> {
            SegmentType type = SegmentType.byType(segment.getType());
                if (SegmentType.LOBINDEX.equals(type)) {
                    Optional<Command> isDependent = commands.parallelStream()
                            .filter(command -> command.dependentSegments().parallelStream().anyMatch(depended -> Objects.equals(segment, depended)))
                            .findFirst();

                    if (isDependent.isPresent()) {
                        Logger.getContextLogger(SQLGenerator.class).debug(
                                "Index already added as dependency:\n* Index:   {0}\n* Command: {1}",
                                segment, isDependent.get().getTitle(Direction.Forward)
                        );
                    } else {
                        Lob lobCommand = Lob.getLob(segment);
                        if (lobCommand != null) {
                            commands.add(lobCommand);
                        }
                    }
                }
        });
        preparedSegments.forEach(segment -> {
            SegmentType type = SegmentType.byType(segment.getType());
            if (type != null) {
                switch (type) {
                    case INDEX:
                    case INDEX_PARTITION:
                    case INDEX_SUBPARTITION:
                        {
                        Optional<Command> isDependent = commands.parallelStream()
                                .filter(command -> command.dependentSegments().parallelStream().anyMatch(depended -> Objects.equals(segment, depended)))
                                .findFirst();

                        if (isDependent.isPresent()) {
                            Logger.getContextLogger(SQLGenerator.class).debug(
                                    "Index already added as dependency:\n* Index:   {0}\n* Command: {1}",
                                    segment, isDependent.get().getTitle(Direction.Forward)
                            );
                        } else {
                            if (!indexType.containsKey(segment.getName())) {
                                indexType.put(segment.getName(), Index.getIndexType(segment));
                            }
                            if ("LOB".equals(indexType.get(segment.getName()))) {
                                commands.add(Lob.getCommandsByIndex(segment));
                            } else {
                                Index idxCommand = new Index(segment);
                                if (!commands.contains(idxCommand)) {
                                    commands.add(idxCommand);
                                }
                            }
                        }
                        break;
                    }
                }
            }
        });

        // Generate index rebuild commands
        Logger.getContextLogger(SQLGenerator.class).debug("Generate index rebuild commands");
        List<String> users = preparedSegments.parallelStream()
                .map(Segment::getOwner)
                .distinct()
                .collect(Collectors.toList());
        final String query = MessageFormat.format(
                Language.get(Command.class, "findIndexes@query"),
                users.stream()
                        .map(user -> MessageFormat.format("''{0}''", user))
                        .collect(Collectors.joining(","))
        );
        List<String> tables = Stream.concat(
                    preparedSegments.stream(),
                    new LinkedList<>(commands).stream()
                            .filter(command -> command instanceof Lob)
                            .map(command -> ((Lob) command).tableSegment))
                .filter(segment -> SegmentType.TABLE.equals(SegmentType.byType(segment.getType())))
                .map(Segment::getName)
                .distinct()
                .collect(Collectors.toList());
        try (final ResultSet resultSet = ServiceRegistry.getInstance()
                .lookupService(IDatabaseAccessService.class)
                .select(controller.getTableSpace().getConnectionID(), query)
        ) {
            while (resultSet.next()) {
                final String space = resultSet.getString("TABLESPACE_NAME");
                final String owner = resultSet.getString("TABLE_OWNER");
                final String table = resultSet.getString("TABLE_NAME");
                final String index = resultSet.getString("INDEX_NAME");
                if (tables.contains(table)) {
                    Index idxCommand = new Index(new Index.IndexDef(space, owner, index));
                    if (!commands.contains(idxCommand)) {
                        commands.add(idxCommand);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commands;
    }

    private void generateRepairCommands() {
        TaskOutput.put(Level.Info, "<br>"+Language.get(DefragmentationTask.class, "step@repair"));
        try (final ResultSet resultSet = ServiceRegistry.getInstance()
                .lookupService(IDatabaseAccessService.class)
                .select(
                        controller.getTableSpace().getConnectionID(),
                        "SELECT OWNER, SEGMENT_TYPE, SEGMENT_NAME, PARTITION_NAME FROM DBA_SEGMENTS WHERE TABLESPACE_NAME = ?",
                        Space.getNewSpaceName(spaceName.get())
                )
        ) {
            List<Segment> segments = new LinkedList<>();
            while (resultSet.next()) {
                final String owner = resultSet.getString("OWNER");
                final String name  = resultSet.getString("SEGMENT_NAME");
                final String part  = resultSet.getString("PARTITION_NAME");
                SegmentType  type  = SegmentType.byType(resultSet.getString("SEGMENT_TYPE"));

                if (type != null) {
                    segments.add(new Segment(owner, type.getType(), name, part) {
                        @Override
                        public TableSpace getTableSpace() {
                            return controller.getTableSpace();
                        }

                        @Override
                        public Supplier<List<IProblematic>> problemsGetter() {
                            return Collections::emptyList;
                        }
                    });
                }
            }
            if (!segments.isEmpty()) {
                Logger.getContextLogger(TableSpaceManager.class).debug(
                        "Found not moved {0} segments:\n{1}",
                        String.valueOf(segments.size()),
                        segments.stream()
                                .map(segment -> MessageFormat.format("* {0}", segment))
                                .collect(Collectors.joining("\n"))
                );
            }

            IFormController.IDataProvider prevProvider = getProvider();
            setProvider(new IFormController.IDataProvider() {
                @Override
                public List<Segment> getSegments() {
                    return Stream.concat(
                            segments.stream(),
                            controller.getDataProvider().getSegments().stream()
                    ).collect(Collectors.toList());
                }
            });

            List<Command> repairCommands = prepareCommands(segments, true);
            // Move Quota commands to the end
            repairCommands.sort((o1, o2) -> {
                if (o1 instanceof Quota && !(o2 instanceof Quota)) {
                    return 1;
                } else if (o2 instanceof Quota && !(o1 instanceof Quota)) {
                    return -1;
                }
                return 0;
            });

            TaskOutput.put(
                    Level.Info,
                    Language.get(DefragmentationTask.class, "step@repair.result"),
                    repairCommands.size()
            );
            repairCommands.stream()
                    .map(command -> MessageFormat.format(
                            "<font color='blue'>{0};</font>",
                            command.getSQL(Direction.Backward)
                    ))
                    .forEach(query -> TaskOutput.put(Level.Info, query));
            setProvider(prevProvider);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeProcess() throws Exception {
        TaskOutput.put(Level.Info,Language.get("step@initialize"));
        setWaitLockTimeout();
        createTablespace();
    }

    private void finalizeProcess() {
        TaskOutput.put(Level.Info, Language.get(DefragmentationTask.class, "step@finalize"));
        try {
            checkTablespace();
            dropTablespace();
        } catch (Exception ignore) {}
    }

    private void setWaitLockTimeout() {
        try {
            executeCommandSQL(MessageFormat.format("ALTER SYSTEM SET DDL_LOCK_TIMEOUT={0}", DDL_LOCK_TIMEOUT));
        } catch (SQLException e) {
            Logger.getContextLogger(TableSpaceManager.class).error("Database error", e);
        }
    }

    private void createTablespace() throws Exception {
        final Command createTbs = new Space(controller.getTableSpace(), Space.Operation.CREATE);
        final String  commandTitle = createTbs.getTitle(Direction.Forward);
        try {
            setProgress(getProgress(), createTbs.getTitle(Direction.Forward));
            Logger.getContextLogger(TableSpaceManager.class).debug("Create temporary tablespace");
            executeCommandSQL(createTbs.getSQL(Direction.Forward));
            TaskOutput.put(Level.Info, "<font color='green'>&#x2611;</font> {0}", commandTitle);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1543) {
                //ORA-01543: Tablespace already exists
                TaskOutput.put(Level.Debug, "&#x2610; {0} (Skip)", commandTitle);
            } else {
                Logger.getContextLogger(TableSpaceManager.class).warn(e.getMessage().trim());
                TaskOutput.put(Level.Error, "&#x2612; {0}<br><b>&nbsp;&nbsp;&nbsp;{1}</b>", commandTitle, e.getMessage());
                throw e;
            }
        }
    }

    private void checkTablespace() throws Exception {
        final String step = Language.get(DefragmentationTask.class, "tbs@check");
        setProgress(getProgress(), step);
        Logger.getContextLogger(TableSpaceManager.class).debug("Check temporary tablespace is empty");
        try (final ResultSet resultSet = ServiceRegistry.getInstance()
                .lookupService(IDatabaseAccessService.class).select(
                        controller.getTableSpace().getConnectionID(),
                        "SELECT COUNT(*) FROM DBA_SEGMENTS WHERE TABLESPACE_NAME = ?",
                        Space.getNewSpaceName(spaceName.get())
                )
        ) {
            if (!resultSet.next() || resultSet.getInt(1) != 0) {
                Logger.getContextLogger(TableSpaceManager.class).error(
                        "Tablespace {0} is not empty",
                        Space.getNewSpaceName(spaceName.get())
                );
                TaskOutput.put(
                        Level.Error,
                        Language.get(DefragmentationTask.class, "tbs@check.fail"),
                        step, Space.getNewSpaceName(spaceName.get())
                );
                generateRepairCommands();
                throw new Exception();
            }
            TaskOutput.put(
                    Level.Info,
                    "<font color='green'>&#x2611;</font> {0}",
                    step
            );
        }
    }

    private void dropTablespace() throws Exception {
        // Release tablespace
        {
            final Command offlineTbs = new Space(controller.getTableSpace(), Space.Operation.RELEASE);
            final String commandTitle = offlineTbs.getTitle(Direction.Forward);
            try {
                setProgress(getProgress(), commandTitle);
                Logger.getContextLogger(TableSpaceManager.class).debug("Release temporary tablespace");
                executeCommandSQL(offlineTbs.getSQL(Direction.Forward));
                TaskOutput.put(Level.Info, "<font color='green'>&#x2611;</font> {0}", commandTitle);
            } catch (SQLException e) {
                Logger.getContextLogger(TableSpaceManager.class).warn(e.getMessage().trim());
                TaskOutput.put(Level.Error, "&#x2612; {0}<br><b>&nbsp;&nbsp;&nbsp;{1}</b>", commandTitle, e.getMessage());
                throw e;
            }
        }
        // Drop tablespace
        {
            final Command dropTbs = new Space(controller.getTableSpace(), Space.Operation.DROP);
            final String commandTitle = dropTbs.getTitle(Direction.Forward);
            try {
                Logger.getContextLogger(TableSpaceManager.class).debug("Drop temporary tablespace");
                setProgress(getProgress(), commandTitle);
                executeCommandSQL(dropTbs.getSQL(Direction.Forward));
                TaskOutput.put(Level.Info, "<font color='green'>&#x2611;</font> {0}", commandTitle);
            } catch (SQLException e) {
                Logger.getContextLogger(TableSpaceManager.class).warn(e.getMessage().trim());
                TaskOutput.put(Level.Error, "&#x2612; {0}<br><b>&nbsp;&nbsp;&nbsp;{1}</b>", commandTitle, e.getMessage());
                throw e;
            }
        }
    }

    private void process(List<Command> commands) {
        long totalCommands = commandsCount(commands);
        // TODO: Calc progress based on moved size
        AtomicInteger commandIdx = new AtomicInteger(0);
        try {
            // Process forward
            TaskOutput.put(Level.Info, Language.get(DefragmentationTask.class, "step@move.forward"));
            commands.stream().filter(Command::isForward).forEach(command -> {
                String commandTitle = command.getTitle(Direction.Forward);
                if (!isCancelled()) {
                    int percent = (int) (100 * commandIdx.addAndGet(1) / totalCommands);
                    setProgress(percent, commandTitle);
                    processCommand(command, Direction.Forward, commandIdx.get(), totalCommands, commandTitle);
                } else {
                    throw new CancelException();
                }
            });

            //Coalesce tablespace
            Logger.getContextLogger(TableSpaceManager.class).debug("Coalesce original tablespace");
            controller.getTableSpace().coalesce();

            // Compact initial tablespace
//            controller.getTableSpace().getDatafiles().forEach(dataFile -> {
//                if (dataFile.isAutoExtensible()) {
//                    try {
//                        long curSize = dataFile.getBlocks() * controller.getTableSpace().getBlockSize();
//                        long minSize = dataFile.getMinimalSize();
//                        Logger.getContextLogger(TableSpaceManager.class).debug(
//                                "Compact datafile ''{0}'': {1} -> {2}",
//                                dataFile.getFileName(),
//                                FileUtils.formatFileSize(curSize, FileUtils.Dimension.MB),
//                                FileUtils.formatFileSize(minSize, FileUtils.Dimension.MB)
//                        );
//                        dataFile.resize(DataFile.truncateSize(minSize));
//                    } catch (SQLException e) {
//                        Logger.getContextLogger(TableSpaceManager.class).warn("Resize error: {0}", e.getMessage());
//                    }
//                }
//            });

            // Move Quota commands to the end
            commands.sort((o1, o2) -> {
                if (o1 instanceof Quota && !(o2 instanceof Quota)) {
                    return 1;
                } else if (o2 instanceof Quota && !(o1 instanceof Quota)) {
                    return -1;
                }
                return 0;
            });

            // Process backward
            TaskOutput.put(Level.Info, Language.get(DefragmentationTask.class, "step@move.backward"));
            commands.stream().filter(Command::isBackward).forEach(command -> {
                int percent = (int) (100 * commandIdx.getAndAdd(1) / totalCommands);
                String commandTitle = command.getTitle(Direction.Backward);
                setProgress(percent, commandTitle);

                if (!isCancelled()) {
                    processCommand(command, Direction.Backward, commandIdx.get(), totalCommands, commandTitle);
                } else {
                    throw new CancelException();
                }
            });
        } catch (Throwable e) {
            TaskOutput.put(isCancelled() ? Level.Warn : Level.Error, Language.get("result@abort"));
            if (!isCancelled()) {
                Logger.getContextLogger(TableSpaceManager.class).error("Unexpected error", e);
            }
        }
    }

    private void processCommand(
            Command command,
            Direction direction,
            int    index,
            long   total,
            String title
    ) {
        final String query = command.getSQL(direction);
        try {
            executeCommandSQL(query);
            TaskOutput.put(Level.Info, COMMAND_SUCCESS, String.valueOf(index), String.valueOf(total), title);
        } catch (SQLException e) {
            if (e.getErrorCode() == 2149) {
                //ORA-02149: Specified partition does not exist
                Logger.getContextLogger(TableSpaceManager.class).debug(
                        "{0}\n{1}",
                        query,
                        "Object (sub)partition does not exists"
                );
                TaskOutput.put(Level.Debug, COMMAND_SKIP, String.valueOf(index), String.valueOf(total), title);
            } else {
                Logger.getContextLogger(TableSpaceManager.class).warn( "{0}\n{1}", query, e.getMessage().trim());
                TaskOutput.put(Level.Error, COMMAND_ERROR, String.valueOf(index), String.valueOf(total), title, e.getMessage().trim());
            }
        }
    }

    private void executeCommandSQL(String query) throws SQLException {
        try (PreparedStatement commandStatement = ServiceRegistry.getInstance()
                .lookupService(IDatabaseAccessService.class).prepareStatement(
                    controller.getTableSpace().getConnectionID(),
                    query
            )
        ) {
            checkPaused();
            commandStatement.executeUpdate();
            Connection connection = commandStatement.getConnection();
            commandStatement.close();
            connection.close();
        }
    }

    @Override
    public void finished(Void result) {}


    enum Direction {
        Forward, Backward, Any
    }

    abstract static class Command {

        private final String owner, table, name, part;
        private       String space;

        Command(String space, String owner, String table, String name, String part) {
            this.space = space;
            this.owner = owner;
            this.table = table;
            this.name  = name;
            this.part  = part;
        }

        String getOwner() {
            return owner;
        }

        String getTable() {
            return table;
        }

        String getName() {
            return name;
        }

        String getPart() {
            return part;
        }

        String getSpace() {
            return space;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Command command = (Command) o;
            return  Objects.equals(space, command.space) &&
                    Objects.equals(owner, command.owner) &&
                    Objects.equals(table, command.table) &&
                    Objects.equals(name,  command.name) &&
                    Objects.equals(part,  command.part);
        }

        @Override
        public int hashCode() {
            return Objects.hash(space, owner, table, name, part);
        }

        protected abstract Direction getDirection();
        final boolean isForward() {
            return getDirection() == Direction.Any || getDirection() == Direction.Forward;
        }
        final boolean isBackward() {
            return getDirection() == Direction.Any || getDirection() == Direction.Backward;
        }

        abstract String getSQL(Direction direction);
        abstract String getTitle(Direction direction);
        abstract Collection<Segment> dependentSegments();
    }


    static class Space extends Command {

        private final static String SIZE_INIT = "1M";
        private final static String SIZE_NEXT = "200M";

        enum Operation {
            CREATE, RELEASE, DROP
        }

        private static String getNewSpaceName(String originalSpace) {
            return originalSpace.concat("_TMP");
        }

        private static String getNewSpaceFile(String originalFile) {
            final File file = new File(originalFile);
            final String name = file.getName();
            final String base = FilenameUtils.removeExtension(name);
            return FilenameUtils.getFullPath(originalFile).concat(base).concat(".TMP");
        }

        private final Operation operation;

        Space(TableSpace originalSpace, Operation operation) {
            super(
                    originalSpace.getPID(),
                    null, null,
                    originalSpace.getDatafiles().get(0).getFileName(),
                    null
            );
            this.operation = operation;
        }

        @Override
        protected Direction getDirection() {
            return Direction.Forward;
        }

        @Override
        String getSQL(Direction direction) {
            switch (operation) {
                case CREATE: return MessageFormat.format(
                        "CREATE BIGFILE TABLESPACE {0} DATAFILE ''{1}'' SIZE {2} AUTOEXTEND ON NEXT {3} SEGMENT SPACE MANAGEMENT AUTO",
                        getNewSpaceName(getSpace()),
                        getNewSpaceFile(getName()),
                        SIZE_INIT, SIZE_NEXT
                );
                case RELEASE: return MessageFormat.format(
                        "ALTER DATABASE DATAFILE ''{0}'' OFFLINE DROP",
                        getNewSpaceFile(getName())
                );
                case DROP: return MessageFormat.format(
                        "DROP TABLESPACE {0} INCLUDING CONTENTS AND DATAFILES CASCADE CONSTRAINTS",
                        getNewSpaceName(getSpace())
                );
                default: return null;
            }
        }

        @Override
        String getTitle(Direction direction) {
            switch (operation) {
                case CREATE: return MessageFormat.format(
                        Language.get(DefragmentationTask.class, "tbs@create"),
                        getNewSpaceName(getSpace())
                );
                case RELEASE: return MessageFormat.format(
                        Language.get(DefragmentationTask.class, "tbs@release"),
                        getNewSpaceFile(getName())
                );
                case DROP: return MessageFormat.format(
                        Language.get(DefragmentationTask.class, "tbs@drop"),
                        getNewSpaceName(getSpace())
                );
                default: return null;
            }
        }

        @Override
        Collection<Segment> dependentSegments() {
            return Collections.emptyList();
        }
    }


    static class Quota extends Command {

        private final static String QUOTA_ON  = "UNLIMITED";
        private final static String QUOTA_OFF = "0";

        Quota(String space, String owner) {
            super(space, owner, null, null, null);
        }

        @Override
        protected Direction getDirection() {
            return Direction.Any;
        }

        @Override
        String getSQL(Direction direction) {
            return MessageFormat.format(
                    "ALTER USER {0} QUOTA {1} ON {2}",
                    getOwner(),
                    direction.equals(Direction.Forward) ? QUOTA_ON : QUOTA_OFF,
                    Space.getNewSpaceName(getSpace())
            );
        }

        @Override
        String getTitle(Direction direction) {
            return MessageFormat.format(
                    Language.get(DefragmentationTask.class, direction.equals(Direction.Forward) ? "access@grant" : "access@revoke"),
                    getOwner(), Space.getNewSpaceName(getSpace())
            );
        }

        @Override
        Collection<Segment> dependentSegments() {
            return Collections.emptyList();
        }
    }


    static class Table extends Command {

        final static String OPT_PARTITION    = "PARTITION";
        final static String OPT_SUBPARTITION = "SUBPARTITION";
        final static String OPT_UPDATE_INDEX = "UPDATE INDEXES";

        private final Segment tableSegment;
        private final List<Segment> dependentSegments = new LinkedList<>();

        Table(Segment segment) {
            super(
                    segment.getTableSpace().getPID(),
                    segment.getOwner(),
                    segment.getName(),
                    null,
                    segment.getPart()
            );
            this.tableSegment = segment;
            dependentSegments.add(tableSegment);
        }

        @Override
        protected  Direction getDirection() {
            return Direction.Any;
        }

        @Override
        String getSQL(Direction direction) {
            boolean isSubPartition = SegmentType.TABLE_SUBPARTITION.equals(SegmentType.byType(tableSegment.getType()));
            return MessageFormat.format(
                    "ALTER TABLE {0}.{1} MOVE {2} {3} TABLESPACE {4} {5}",
                    new ArrayList<String>() {{
                        add(getOwner());
                        add(getTable());
                        add(getPart() != null ? (isSubPartition ? OPT_SUBPARTITION : OPT_PARTITION) : "");
                        add(getPart() != null ? getPart() : "");
                        add(direction.equals(Direction.Forward) ? Space.getNewSpaceName(getSpace()) : getSpace());
                        add(getPart() != null ? OPT_UPDATE_INDEX : "");
                    }}.toArray()
            ).replaceAll("\\s\\s+", " ").trim();
        }

        @Override
        public String getTitle(Direction direction) {
            return MessageFormat.format(
                    Language.get(DefragmentationTask.class, getPart() == null ? "move@table" : "move@table.part"),
                    new ArrayList<String>() {{
                        add(getOwner());
                        add(getTable());
                        if (getPart() != null) {
                            add(getPart());
                        }
                        add(FileUtils.formatFileSize(dependentSegments().parallelStream()
                                .mapToLong(Segment::getSize)
                                .sum()
                        ));
                    }}.toArray()
            );
        }

        @Override
        Collection<Segment> dependentSegments() {
            return dependentSegments;
        }
    }


    static class Index extends Command {

        static String getIndexType(Segment index) {
            final String query = "SELECT INDEX_TYPE FROM DBA_INDEXES WHERE OWNER = ? AND INDEX_NAME = ?";
            try (final ResultSet resultSet = ServiceRegistry.getInstance().lookupService(IDatabaseAccessService.class)
                    .select(index.getTableSpace().getConnectionID(), query, index.getOwner(), index.getName())
            ) {
                if (resultSet.next()) {
                    return resultSet.getString("INDEX_TYPE");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        private final Segment indexSegment;

        static class IndexDef {
            private final String space, owner, index;

            IndexDef(String space, String owner, String index) {
                this.space = space;
                this.owner = owner;
                this.index = index;
            }
        }

        Index(Segment segment) {
            super(
                    segment.getTableSpace().getPID(),
                    segment.getOwner(),
                    null,
                    segment.getName(),
                    segment.getPart()
            );
            this.indexSegment = segment;
        }

        Index(IndexDef indexDef) {
            super(indexDef.space, indexDef.owner, null, indexDef.index, null);
            this.indexSegment = null;
        }

        @Override
        protected Direction getDirection() {
            return indexSegment == null ? Direction.Backward : Direction.Any;
        }

        @Override
        String getSQL(Direction direction) {
            boolean isSubPartition = indexSegment != null && SegmentType.INDEX_SUBPARTITION.equals(SegmentType.byType(indexSegment.getType()));
            return MessageFormat.format(
                    "ALTER INDEX {0}.{1} REBUILD {2} {3} TABLESPACE {4}",
                    new ArrayList<String>() {{
                        add(getOwner());
                        add(getName());
                        add(getPart() != null ? (isSubPartition ? Table.OPT_SUBPARTITION : Table.OPT_PARTITION) : "");
                        add(getPart() != null ? getPart() : "");
                        add(direction.equals(Direction.Forward) ? Space.getNewSpaceName(getSpace()) : getSpace());
                    }}.toArray()
            ).replaceAll("\\s\\s+", " ").trim();
        }

        @Override
        String getTitle(Direction direction) {
            if (indexSegment == null) {
                return MessageFormat.format(
                        Language.get(DefragmentationTask.class, "rebuild@index"),
                        new ArrayList<String>() {{
                            add(getOwner());
                            add(getName());
                            add(getSpace());
                        }}.toArray()
                );
            } else {
                return MessageFormat.format(
                        Language.get(DefragmentationTask.class, getPart() == null ? "move@index" : "move@index.part"),
                        new ArrayList<String>() {{
                            add(getOwner());
                            add(getName());
                            if (getPart() != null) {
                                add(getPart());
                            }
                            add(FileUtils.formatFileSize(dependentSegments().parallelStream()
                                    .mapToLong(Segment::getSize)
                                    .sum()
                            ));
                        }}.toArray()
                );
            }
        }

        @Override
        Collection<Segment> dependentSegments() {
            return indexSegment == null ? Collections.emptyList() : Collections.singletonList(indexSegment);
        }
    }


    static class Lob extends Command {

        static Lob getLob(Segment segment) {
            SegmentType segmentType = SegmentType.byType(segment.getType());
            if (segmentType != null) {
                switch (segmentType) {
                    case LOB:
                    case LOBINDEX:
                        return getLobBySegment(segment);
                    case LOB_PARTITION:
                    case LOB_SUBPARTITION:
                        return getLobByPartition(segment);
                }
            }
            return null;
        }

        private static Lob getLobBySegment(Segment lobSegment) {
            try (final ResultSet resultSet = ServiceRegistry.getInstance()
                    .lookupService(IDatabaseAccessService.class)
                    .select(
                            lobSegment.getTableSpace().getConnectionID(),
                            Language.get(Command.class, "query@findLobBySegment"),
                            lobSegment.getOwner(),
                            lobSegment.getType(), lobSegment.getName(),
                            lobSegment.getType(), lobSegment.getName()
                    )
            ) {
                while (resultSet.next()) {
                    final String space  = resultSet.getString("TABLESPACE_NAME");
                    final String owner  = resultSet.getString("OWNER");
                    final String table  = resultSet.getString("TABLE_NAME");
                    final String column = resultSet.getString("COLUMN_NAME");

                    Optional<Segment> tableSegment = getProvider().getSegments().parallelStream()
                            .filter(segment ->
                                    Objects.equals(segment.getOwner(), owner) &&
                                    Objects.equals(segment.getName(),  table)
                            ).findFirst();
                    if (tableSegment.isPresent()) {
                        Logger.getContextLogger(SQLGenerator.class).debug(
                                "Found lob -> table pair:\n* Lob:   {0}\n* Table: {1} (column={2})",
                                lobSegment, tableSegment.get(), column
                        );
                        return new Lob(lobSegment, tableSegment.get(), column);
                    } else {
                        if (!space.equals(lobSegment.getTableSpace().getPID())) {
                            return new Lob(
                                    lobSegment,
                                    new Segment(owner, SegmentType.TABLE.getType(), table, null) {
                                        @Override
                                        public TableSpace getTableSpace() {
                                            return lobSegment.getTableSpace();
                                        }

                                        @Override
                                        public Supplier<List<IProblematic>> problemsGetter() {
                                            return Collections::emptyList;
                                        }
                                    },
                                    column
                            );
                        } else {
                            Logger.getContextLogger(SQLGenerator.class).warn(
                                    "Pair lob -> table not found:\n* Lob:   {0}",
                                    lobSegment
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        private static Lob getLobByPartition(Segment lobPartSegment) {
            final String query;
            if (lobPartSegment.getType().equals(SegmentType.LOB_PARTITION.getType())) {
                query = Language.get(Command.class, "query@findLobByPart");
            } else {
                query = Language.get(Command.class, "query@findLobBySubPart");
            }
            try (final ResultSet resultSet = ServiceRegistry.getInstance()
                    .lookupService(IDatabaseAccessService.class)
                    .select(
                            lobPartSegment.getTableSpace().getConnectionID(),
                            query,
                            lobPartSegment.getOwner(),
                            lobPartSegment.getName(),
                            lobPartSegment.getPart()
                    )
            ) {
                while (resultSet.next()) {
                    final String space  = resultSet.getString("TABLESPACE_NAME");
                    final String owner  = resultSet.getString("TABLE_OWNER");
                    final String table  = resultSet.getString("TABLE_NAME");
                    final String column = resultSet.getString("COLUMN_NAME");
                    final String part   = resultSet.getString("PARTITION_NAME");

                    Optional<Segment> tablePartSegment = getProvider().getSegments().parallelStream()
                            .filter(segment ->
                                    Objects.equals(segment.getOwner(), owner) &&
                                    Objects.equals(segment.getName(),  table) &&
                                    Objects.equals(segment.getPart(),  part)
                            ).findFirst();
                    if (tablePartSegment.isPresent()) {
                        Logger.getContextLogger(SQLGenerator.class).debug(
                                "Found lob -> table pair:\n* Lob:   {0}\n* Table: {1} (column={2})",
                                lobPartSegment, tablePartSegment.get(), column
                        );
                        return new Lob(lobPartSegment, tablePartSegment.get(), column);
                    } else {
                        if (!space.equals(lobPartSegment.getTableSpace().getPID())) {
                            Logger.getContextLogger(SQLGenerator.class).warn(
                                    "Lob and table are located in different tablespaces:\n* Lob:   {0}\n* Table: {1}",
                                    lobPartSegment.getTableSpace().getPID(), space
                            );
                        }
                        Logger.getContextLogger(SQLGenerator.class).warn(
                                "Pair lob -> table not found:\n* Lob:   {0}",
                                lobPartSegment
                        );
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        private static Lob getCommandsByIndex(Segment indexPartSegment) {
            final String query;
            if (indexPartSegment.getType().equals(SegmentType.INDEX_SUBPARTITION.getType())) {
                query = Language.get(Command.class, "query@findLobByIndexSubPart");
            } else {
                query = Language.get(Command.class, "query@findLobByIndexPart");
            }
            try (final ResultSet resultSet = ServiceRegistry.getInstance().
                    lookupService(IDatabaseAccessService.class)
                    .select(
                            indexPartSegment.getTableSpace().getConnectionID(),
                            query,
                            indexPartSegment.getOwner(), indexPartSegment.getPart()
                    )
            ) {
                while (resultSet.next()) {
                    final String space  = resultSet.getString("TABLESPACE_NAME");
                    final String owner  = resultSet.getString("TABLE_OWNER");
                    final String table  = resultSet.getString("TABLE_NAME");
                    final String column = resultSet.getString("COLUMN_NAME");
                    final String part   = resultSet.getString("PARTITION_NAME");

                    Optional<Segment> tablePartSegment = getProvider().getSegments().parallelStream()
                            .filter(segment ->
                                    Objects.equals(segment.getOwner(), owner) &&
                                    Objects.equals(segment.getName(),  table) &&
                                    Objects.equals(segment.getPart(),  part)
                            ).findFirst();
                    if (tablePartSegment.isPresent()) {
                        Logger.getContextLogger(SQLGenerator.class).debug(
                                "Found index -> table pair:\n* Index: {0}\n* Table: {1} (column={2})",
                                indexPartSegment, tablePartSegment.get(), column
                        );
                        return new Lob(indexPartSegment, tablePartSegment.get(), column);
                    } else {
                        if (!space.equals(indexPartSegment.getTableSpace().getPID())) {
                            Logger.getContextLogger(SQLGenerator.class).warn(
                                    "Index and table are located in different tablespaces:\n* Lob:   {0}\n* Table: {1}",
                                    indexPartSegment.getTableSpace().getPID(), space
                            );
                        }
                        Logger.getContextLogger(SQLGenerator.class).warn(
                                "Pair index -> table not found:\n* Lob:   {0}",
                                indexPartSegment
                        );
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        private final Segment lobSegment, tableSegment;
        private final List<Segment> dependentSegments = new LinkedList<>();

        private Lob(Segment lobSegment, Segment tableSegment, String column) {
            super(
                    lobSegment.getTableSpace().getPID(),
                    tableSegment.getOwner(),
                    tableSegment.getName(),
                    column,
                    tableSegment.getPart()
            );
            this.lobSegment   = lobSegment;
            this.tableSegment = tableSegment;
            dependentSegments.add(lobSegment);
        }

        void addDependency(Segment dependant) {
            dependentSegments.add(dependant);
        }

        List<Segment> findDependantSegments() {
            List<Segment> result = new LinkedList<>();
            boolean isPartitioned = !SegmentType.TABLE.equals(SegmentType.byType(tableSegment.getType()));
            if (isPartitioned) {
                try (final ResultSet resultSet = ServiceRegistry.getInstance()
                        .lookupService(IDatabaseAccessService.class)
                        .select(
                                tableSegment.getTableSpace().getConnectionID(),
                                Language.get(Command.class, "query@findIndexPartsOfLob"),
                                tableSegment.getOwner(),
                                tableSegment.getName(),
                                tableSegment.getPart(),
                                // UNION
                                tableSegment.getOwner(),
                                tableSegment.getName(),
                                tableSegment.getPart()
                        )
                ) {
                    boolean isSubPartition = SegmentType.TABLE_SUBPARTITION.equals(SegmentType.byType(tableSegment.getType()));
                    while (resultSet.next()) {
                        final String space  = resultSet.getString("TABLESPACE_NAME");
                        final String index  = resultSet.getString("INDEX_NAME");
                        final String part   = resultSet.getString("PARTITION_NAME");

                        Optional<Segment> indexSegment = getProvider().getSegments().parallelStream()
                                .filter(segment ->
                                        segment.getOwner().equals(tableSegment.getOwner()) &&
                                        segment.getType().equals(
                                                isSubPartition ?
                                                        SegmentType.INDEX_SUBPARTITION.getType() :
                                                        SegmentType.INDEX_PARTITION.getType()
                                        ) &&
                                        segment.getName().equals(index) &&
                                        Objects.equals(segment.getPart(), part)
                                ).findFirst();
                        if (indexSegment.isPresent()) {
                            Logger.getContextLogger(SQLGenerator.class).debug(
                                    "Found lob -> index dependency:\n* Lob:   {0}\n* Index: {1}",
                                    tableSegment, indexSegment.get()
                            );
                            result.add(indexSegment.get());
                        } else {
                            Logger.getContextLogger(SQLGenerator.class).warn(
                                    "Lob and index are located in different tablespaces:\n* Table: {0}\n* Index: {1}",
                                    tableSegment.getTableSpace().getPID(), space
                            );
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                try (final ResultSet resultSet = ServiceRegistry.getInstance()
                        .lookupService(IDatabaseAccessService.class)
                        .select(
                                lobSegment.getTableSpace().getConnectionID(),
                                Language.get(Command.class, "query@findIndexOfLob"),
                                lobSegment.getOwner(),
                                lobSegment.getName()
                        )
                ) {
                    while (resultSet.next()) {
                        final String space  = resultSet.getString("TABLESPACE_NAME");
                        final String owner  = resultSet.getString("OWNER");
                        final String index  = resultSet.getString("INDEX_NAME");

                        Optional<Segment> indexSegment = getProvider().getSegments().parallelStream()
                                .filter(segment ->
                                        segment.getOwner().equals(tableSegment.getOwner()) &&
                                                segment.getType().equals(SegmentType.LOBINDEX.getType()) &&
                                                segment.getName().equals(index) &&
                                                segment.getPart() == null
                                ).findFirst();
                        if (indexSegment.isPresent()) {
                            Logger.getContextLogger(SQLGenerator.class).debug(
                                    "Found lob -> index dependency:\n* Lob:   {0}\n* Index: {1}",
                                    tableSegment, indexSegment.get()
                            );
                            result.add(indexSegment.get());
                        } else {
                            Logger.getContextLogger(SQLGenerator.class).warn(
                                    "Lob and index are located in different tablespaces:\n* Table: {0}\n* Index: {1}",
                                    tableSegment.getTableSpace().getPID(), space
                            );
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }

        @Override
        protected Direction getDirection() {
            return Direction.Any;
        }

        @Override
        String getSQL(Direction direction) {
            boolean isSubPartition = SegmentType.TABLE_SUBPARTITION.equals(SegmentType.byType(tableSegment.getType()));
            return MessageFormat.format(
                    "ALTER TABLE {0}.{1} MOVE {2} {3} LOB ({4}) STORE AS (TABLESPACE {5}) {6}",
                    new ArrayList<String>() {{
                        add(getOwner());
                        add(getTable());
                        add(getPart() != null ? (isSubPartition ? Table.OPT_SUBPARTITION : Table.OPT_PARTITION) : "");
                        add(getPart() != null ? getPart() : "");
                        add(getName());
                        add(direction.equals(Direction.Forward) ? Space.getNewSpaceName(getSpace()) : getSpace());
                        add(getPart() != null ? Table.OPT_UPDATE_INDEX : "");
                    }}.toArray()
            ).replaceAll("\\s\\s+", " ").trim();
        }

        @Override
        String getTitle(Direction direction) {
            return MessageFormat.format(
                    Language.get(DefragmentationTask.class, getPart() == null ? "move@lob" : "move@lob.part"),
                    new ArrayList<String>() {{
                        add(getName());
                        add(getOwner());
                        add(getTable());
                        if (getPart() != null) {
                            add(getPart());
                        }
                        add(FileUtils.formatFileSize(dependentSegments().parallelStream()
                                    .mapToLong(Segment::getSize)
                                    .sum()
                        ));
                    }}.toArray()
            );
        }

        @Override
        Collection<Segment> dependentSegments() {
            return dependentSegments;
        }
    }
}