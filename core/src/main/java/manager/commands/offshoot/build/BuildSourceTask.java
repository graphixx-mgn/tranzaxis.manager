package manager.commands.offshoot.build;

import codex.log.Logger;
import codex.task.*;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.offshoot.BuildWC;
import manager.nodes.Offshoot;
import manager.type.BuildStatus;
import manager.upgrade.UpgradeService;
import org.apache.log4j.lf5.viewer.categoryexplorer.TreeModelAdapter;
import org.apache.tools.ant.util.DateUtils;
import org.radixware.kernel.common.check.RadixProblem;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BuildSourceTask extends AbstractTask<Error> {

    private final static ImageIcon ICON_ERROR = ImageUtils.resize(ImageUtils.getByPath("/images/cancel.png"), 16, 16);
    private final static ImageIcon ICON_WARNING = ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), 16, 16);

    private final static ImageIcon ICON_EXPAND = ImageUtils.resize(ImageUtils.getByPath("/images/expand.png"), 10, 10);
    private final static ImageIcon ICON_COLLAPSE = ImageUtils.resize(ImageUtils.getByPath("/images/collapse.png"), 10, 10);

    private final Offshoot offshoot;
    private final boolean  clean;
    private final Thread  hook = new Thread(() -> {
        if (!getStatus().isFinal()) {
            cancel(true);
        }
    });

    private final EventTreeModel eventsTreeModel = new EventTreeModel();
    private final List<CompilerEvent> eventsList = new LinkedList<CompilerEvent>() {
        @Override
        public boolean add(CompilerEvent compilerEvent) {
            eventsTreeModel.addEvent(compilerEvent);
            return super.add(compilerEvent);
        }
    };

    public BuildSourceTask(Offshoot offshoot, boolean clean) {
        super(Language.get(BuildWC.class, "command@sources"));
        this.offshoot = offshoot;
        this.clean    = clean;
    }

    @Override
    public boolean isPauseable() {
        return true;
    }

    @Override
    public Error execute() throws Exception {
        UUID uuid = UUID.randomUUID();
        final File currentJar = UpgradeService.getCurrentJar();

        final ArrayList<String> command = new ArrayList<>();
        command.add("java");
        command.addAll(offshoot.getJvmDesigner());

        String classPath;
        if (currentJar.isFile()) {
            classPath = currentJar.getName();
        } else {
            classPath = System.getProperty("java.class.path");
        }
        StringJoiner radixBinPath = new StringJoiner(File.separator)
                .add(offshoot.getLocalPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("bin")
                .add("*");
        StringJoiner radixLibPath = new StringJoiner(File.separator)
                .add(offshoot.getLocalPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("lib")
                .add("*");
        classPath = radixBinPath+";"+radixLibPath+";"+classPath;
        command.add("-cp");
        command.add(classPath);

        command.add("-Dport="+BuildWC.getPort());
        command.add("-Duuid="+uuid.toString());
        command.add("-Dpath="+offshoot.getLocalPath());
        command.add("-Dclean="+(clean ? "1" : "0"));

        command.add(SourceBuilder.class.getCanonicalName());

        final ProcessBuilder builder = new ProcessBuilder(command);
        File temp = File.createTempFile("build_trace", ".tmp", new File(offshoot.getLocalPath()));
        temp.deleteOnExit();
        builder.redirectError(temp);
        builder.redirectOutput(temp);

        AtomicReference<Throwable> errorRef = new AtomicReference<>(null);
        BuildWC.getBuildNotifier().addListener(uuid, new IBuildingNotifier.IBuildListener() {
            @Override
            public void error(Throwable ex) {
                errorRef.set(ex);
            }

            @Override
            public void event(RadixProblem.ESeverity severity, String defId, String name, ImageIcon icon, String message) {
                CompilerEvent event = new CompilerEvent(severity, defId, name, icon, message);
                synchronized (eventsList) {
                    eventsList.add(event);
                    //eventsTreeModel.addEvent(event);
                }
                setProgress(getProgress(), getDescription());
            }


            @Override
            public void progress(int percent) {
                BuildSourceTask.this.setProgress(percent, BuildSourceTask.this.getDescription());
            }

            @Override
            public void description(String text) {
                BuildSourceTask.this.setProgress(BuildSourceTask.this.getProgress(), text);
            }

            @Override
            public void isPaused() {
                checkPaused();
            }
        });

        if (currentJar.isFile()) {
            builder.directory(currentJar.getParentFile());
        } else {
            builder.directory(currentJar);
        }

        Runtime.getRuntime().addShutdownHook(hook);
        Process process = builder.start();
        addListener(new ITaskListener() {
            @Override
            public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                if (nextStatus.equals(Status.CANCELLED)) {
                    process.destroy();
                }
            }
        });
        process.waitFor();
        BuildWC.getBuildNotifier().removeListener(uuid);
        Runtime.getRuntime().removeShutdownHook(hook);
        if (process.isAlive()) process.destroy();

        if (errorRef.get() != null) {
            offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), true));
            try {
                offshoot.model.commit(false);
            } catch (Exception e) {
                //
            }
            String message = MessageFormat.format(
                    "BUILD SOURCE [{0}] failed. Total time: {1}",
                    offshoot.getLocalPath(), DateUtils.formatElapsedTime(getDuration())
            );
            throw new ExecuteException(
                    message,
                    message.concat("\n").concat(Logger.stackTraceToString(errorRef.get()))
            );
        }
        if (eventsList.stream().anyMatch(event -> event.getSeverity() == RadixProblem.ESeverity.ERROR)) {
            Map<String, List<String>> errorIndex = new HashMap<>();
            eventsList.stream()
                    .filter(event -> event.getSeverity() == RadixProblem.ESeverity.ERROR)
                    .forEach(event -> {
                        if (!errorIndex.containsKey(event.getDefId())) {
                            errorIndex.put(event.getDefId(), new LinkedList<>());
                        }
                        errorIndex.get(event.getDefId()).add(event.getMessage());
                    });
            offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), true));
            try {
                offshoot.model.commit(false);
            } catch (Exception e) {
                //
            }
            String message = MessageFormat.format(
                    "BUILD SOURCE [{0}] failed. Total time: {1}. Errors: {2}",
                    offshoot.getLocalPath(), DateUtils.formatElapsedTime(getDuration()), getErrorsCount()
            );
            throw new ExecuteException(
                    message,
                    message.concat("\n").concat(
                            errorIndex.entrySet().stream()
                                    .map(entry -> MessageFormat.format(
                                            "[{0}]\n{1}",
                                            entry.getKey(),
                                            entry.getValue().stream()
                                                .map(" - "::concat)
                                                    .collect(Collectors.joining("\n"))
                                    ))
                                    .collect(Collectors.joining("\n"))
                    )
            );
        }
        return null;
    }

    @Override
    public void finished(Error err) {
        if (!isCancelled()) {
            offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), false));
            try {
                offshoot.model.commit(false);
            } catch (Exception e) {
                //
            }
        }
        Logger.getLogger().info(MessageFormat.format(
                "BUILD SOURCE [{0}] {2}. Total time: {1}",
                offshoot.getLocalPath(), DateUtils.formatElapsedTime(getDuration()),
                isCancelled() ? "canceled" : "finished"
        ));
    }

    private long getErrorsCount() {
        return eventsList.stream().filter(event -> event.getSeverity() == RadixProblem.ESeverity.ERROR).count();
    }

    private long getWarningsCount() {
        return eventsList.stream().filter(event -> event.getSeverity() == RadixProblem.ESeverity.WARNING).count();
    }

    @Override
    public AbstractTaskView createView(Consumer<ITask> cancelAction) {
        return new BuildTaskView(this, eventsTreeModel, cancelAction);
    }

    private class BuildTaskView extends TaskView {

        private final JCheckBox showWarnings = new JCheckBox(Language.get(BuildWC.class, "switch@warnings"), false) {{
            setOpaque(false);
        }};
        private final JLabel problemsStatus = new JLabel(getProblemStatusText(), getProblemsStatusIcon(), SwingConstants.LEFT) {{
            setBorder(new EmptyBorder(0, 0, 3, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }};
        private final JLabel problemsStatusSwitch = new JLabel(ICON_COLLAPSE) {{
            setBorder(new EmptyBorder(0, 0, 3, 3));
        }};

        BuildTaskView(ITask task, EventTreeModel treeModel, Consumer<ITask> cancelAction) {
            super(task, cancelAction);
            JComponent statusLabel = (JComponent) getComponent(2);
            statusLabel.setBorder(new EmptyBorder(0, 0, 3, 0));

            JPanel controlPanel = new JPanel(new BorderLayout());
            controlPanel.setOpaque(false);
            controlPanel.add(showWarnings, BorderLayout.WEST);
            controlPanel.add(problemsStatusSwitch, BorderLayout.CENTER);
            controlPanel.add(problemsStatus, BorderLayout.EAST);

            JPanel statusPanel = new JPanel(new BorderLayout());
            statusPanel.setOpaque(false);
            statusPanel.add(statusLabel, BorderLayout.NORTH);
            statusPanel.add(controlPanel, BorderLayout.WEST);

            JPanel problemsView = new JPanel(new BorderLayout());
            problemsView.setVisible(false);
            problemsView.setPreferredSize(new Dimension(getPreferredSize().width, getPreferredSize().height*5));

            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            treeModel.setFilter(compilerEvent ->
                    compilerEvent.getSeverity().equals(RadixProblem.ESeverity.ERROR) || showWarnings.isSelected()
            );
            showWarnings.addItemListener(event -> {
                synchronized (eventsList) {
                    root.removeAllChildren();
                    treeModel.nodeStructureChanged(root);
                    eventsList.forEach(treeModel::addEvent);
                }
            });
            problemsStatus.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (getErrorsCount() + getWarningsCount() > 0) {
                        problemsStatusSwitch.setIcon(problemsView.isVisible() ? ICON_COLLAPSE : ICON_EXPAND);
                        problemsView.setVisible(!problemsView.isVisible());
                    }
                }
            });

            JTree tree = new JTree(treeModel) {
                @Override
                protected void setExpandedState(TreePath path, boolean state) {
                    super.setExpandedState(path, true);
                }
            };
            tree.setBorder(new EmptyBorder(0, 3, 0, 0));
            tree.getModel().addTreeModelListener(new TreeModelAdapter() {
                @Override
                public void treeStructureChanged(TreeModelEvent event) {
                    super.treeStructureChanged(event);
                    tree.expandPath(event.getTreePath());
                }
            });
            tree.setCellRenderer((tree1, value, selected, expanded, leaf, row, hasFocus) -> {
                JLabel label;
                if (value instanceof Definition) {
                    Definition definition = (Definition) value;
                    label = new JLabel(definition.defName, definition.getIcon(), SwingConstants.LEFT);
                    label.setBorder(new EmptyBorder(3, 0, 0, 0));
                } else if (value instanceof Problem) {
                    Problem problem = (Problem) value;
                    label = new JLabel(problem.message, problem.getIcon(), SwingConstants.LEFT);
                    label.setForeground(problem.severity == RadixProblem.ESeverity.ERROR ? Color.decode("#FF3333") : Color.decode("#AA3333"));
                } else {
                    label = new JLabel(value.toString());
                }
                return label;
            });
            tree.expandRow(0);
            tree.setRootVisible(false);

            JScrollPane scroll = new JScrollPane();
            scroll.setViewportView(tree);
            scroll.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));

            problemsView.add(scroll, BorderLayout.CENTER);
            statusPanel.add(problemsView, BorderLayout.SOUTH);
            add(statusPanel, BorderLayout.SOUTH);
        }

        @Override
        public void progressChanged(ITask task, int percent, String description) {
            super.progressChanged(task, percent, description);
            if (problemsStatus != null) {
                problemsStatus.setText(getProblemStatusText());
                problemsStatus.setIcon(getProblemsStatusIcon());
            }
        }

        @Override
        public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
            super.statusChanged(task, prevStatus, nextStatus);
            if (nextStatus.equals(Status.FAILED) && eventsList.stream().anyMatch(event -> event.getSeverity() == RadixProblem.ESeverity.ERROR)) {
                showWarnings.setSelected(false);
            }
        }

        private ImageIcon getProblemsStatusIcon() {
            long errorsCount = getErrorsCount();
            long warningsCount = getWarningsCount();
            if (errorsCount + warningsCount == 0) {
                return null;
            } else if (errorsCount == 0) {
                return ICON_WARNING;
            } else {
                return ICON_ERROR;
            }
        }

        private String getProblemStatusText() {
            long errorsCount = getErrorsCount();
            long warningsCount = getWarningsCount();
            if (errorsCount + warningsCount == 0) {
                return Language.get(BuildWC.class, "problems@none");
            } else if (errorsCount == 0) {
                return MessageFormat.format(Language.get(BuildWC.class, "problems@warnings"), warningsCount);
            } else {
                return MessageFormat.format(Language.get(BuildWC.class, "problems@errors"), errorsCount, warningsCount);
            }
        }
    }

    private class EventTreeModel extends DefaultTreeModel {

        private final DefaultMutableTreeNode root;
        private Predicate<CompilerEvent> filter = compilerEvent -> true;

        EventTreeModel() {
            super(new DefaultMutableTreeNode("Problems"));
            root = (DefaultMutableTreeNode) getRoot();
        }

        void setFilter(Predicate<CompilerEvent> filter) {
            this.filter = filter;
        }

        @SuppressWarnings("unchecked")
        void addEvent(CompilerEvent event) {
            if (filter.test(event)) {
                Definition definition = ((List<Definition>) Collections.list(root.children())).stream()
                        .filter(childDef -> childDef.defId.equals(event.getDefId()))
                        .findFirst().orElse(new Definition(event.getDefId(), event.getName(), event.getIcon()));
                Problem problem = new Problem(event.getSeverity(), event.getMessage());

                if (definition.getParent() == null) {
                    root.add(definition);
                    nodesWereInserted(root, new int[]{root.getIndex(definition)});
                }
                definition.add(problem);
                nodeStructureChanged(definition);
            }
        }

    }

    private class Definition extends DefaultMutableTreeNode implements Iconified {
        private final String defId;
        private final String defName;
        private final ImageIcon defIcon;

        Definition(String defId, String defName, ImageIcon defIcon) {
            this.defId   = defId;
            this.defName = defName;
            this.defIcon = defIcon;
        }

        @Override
        public ImageIcon getIcon() {
            return defIcon;
        }
    }

    private class Problem extends DefaultMutableTreeNode implements Iconified {
        private final RadixProblem.ESeverity severity;
        private final String message;

        Problem(RadixProblem.ESeverity severity, String message) {
            this.severity = severity;
            this.message  = message;
        }


        @Override
        public ImageIcon getIcon() {
            return severity == RadixProblem.ESeverity.ERROR ? ICON_ERROR : ICON_WARNING;
        }
    }

}
