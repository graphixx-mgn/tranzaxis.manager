package manager.commands.offshoot.build;

import codex.log.Logger;
import codex.task.*;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.Runtime;
import manager.commands.offshoot.BuildWC;
import manager.nodes.Offshoot;
import manager.type.BuildStatus;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
    private final Thread   hook = new Thread(() -> {
        if (!getStatus().isFinal()) {
            cancel(true);
        }
    });

    private final EventTreeModel eventsTreeModel = new EventTreeModel();
    private final List<CompilerEvent> problems = new LinkedList<>();

    public BuildSourceTask(Offshoot offshoot, boolean clean) {
        super(MessageFormat.format(
                Language.get(BuildWC.class, "command@sources"),
                offshoot.getRepository().getPID(),
                offshoot.getPID()
        ));
        this.offshoot = offshoot;
        this.clean    = clean;
    }

    @Override
    public boolean isPauseable() {
        return true;
    }

    @Override
    public Error execute() throws Exception {
        BuildWC.RMIRegistry rmiRegistry = new BuildWC.RMIRegistry();
        final File currentJar = Runtime.APP.jarFile.get();
        final List<String> cmdList = new LinkedList<String>() {{
            add("java");
            addAll(offshoot.getJvmDesigner());
            add("-cp");
            add(BuildEnvironment.buildClassPath(offshoot));
            add("-Dport="+rmiRegistry.getPort());
            add("-Dpath="+offshoot.getLocalPath());
            add(SourceBuilder.class.getCanonicalName());
        }};

        final ProcessBuilder builder = new ProcessBuilder(cmdList);
        if (currentJar.isFile()) {
            builder.directory(currentJar.getParentFile());
        } else {
            builder.directory(currentJar);
        }

        AtomicReference<Throwable> errorRef = new AtomicReference<>(null);
        rmiRegistry.registerService(BuildingNotifier.class.getTypeName(), new BuildingNotifier() {
            @Override
            public void error(Throwable ex) {
                errorRef.set(ex);
            }

            @Override
            public void event(RadixProblem.ESeverity severity, String defId, String name, ImageIcon icon, String message) {
                final CompilerEvent event = new CompilerEvent(severity, defId, name, icon, message);
                synchronized (eventsTreeModel) {
                    problems.add(event);
                    eventsTreeModel.registerEvent(event);
                }
                setProgress(getProgress(), getDescription()); // Repaint task widget
            }

            @Override
            public void progress(int percent) {
               setProgress(percent, getDescription());
            }

            @Override
            public void description(String text) {
                setProgress(getProgress(), text);
            }

            @Override
            public void isPaused() {
                checkPaused();
            }
        });

        java.lang.Runtime.getRuntime().addShutdownHook(hook);
        Process process = builder.redirectErrorStream(true).start();
        addListener(new ITaskListener() {
            @Override
            public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                if (nextStatus.equals(Status.CANCELLED)) {
                    process.destroy();
                }
                if (nextStatus.isFinal()) {
                    try {
                        rmiRegistry.close();
                    } catch (IOException ignore) {}
                }
            }
        });

        final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        reader.lines().iterator().forEachRemaining(s -> { /* ignore process output */ });
        process.waitFor();

        java.lang.Runtime.getRuntime().removeShutdownHook(hook);
        if (process.isAlive()) process.destroy();

        if (errorRef.get() == null && process.exitValue() > 0) {
            errorRef.set(new Exception(Language.get(BuildWC.class, "command@halted")));
        }

        if (errorRef.get() != null) {
            offshoot.setBuiltStatus(null);
            offshoot.model.commit(false);
            String message = MessageFormat.format(
                    "Build sources [{0}/{1}] failed. Total time: {2}",
                    offshoot.getRepository().getPID(),
                    offshoot.getPID(),
                    DateUtils.formatElapsedTime(getDuration())
            );
            throw new ExecuteException(
                    Language.get(BuildWC.class, "command@failed"),
                    message.concat("\n").concat(Logger.stackTraceToString(BuildWC.getRootCause(errorRef.get())))
            );
        }

        if (getErrorsCount() > 0) {
            offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), true));
            offshoot.model.commit(false);
            Map<String, List<String>> errorIndex = new HashMap<>();
            problems.stream()
                    .filter(event -> event.getSeverity() == RadixProblem.ESeverity.ERROR)
                    .forEach(event -> {
                        String def = event.getName()+" ("+event.getDefId()+")";
                        if (!errorIndex.containsKey(def)) {
                            errorIndex.put(def, new LinkedList<>());
                        }
                        errorIndex.get(def).add(event.getMessage());
                    });
            throw new ExecuteException(
                    Language.get(BuildWC.class, "modules@errors"),
                    MessageFormat.format(
                            "Build modules [{0}/{1}] failed due to compilation errors.\nNumber of errors: {2}\n{3}",
                            offshoot.getRepository().getPID(),
                            offshoot.getPID(),
                            getErrorsCount(),
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
            Logger.getLogger().info(MessageFormat.format(
                    "Build modules [{0}/{1}] {2}. Total time: {3}",
                    offshoot.getRepository().getPID(),
                    offshoot.getPID(),
                    isCancelled() ? "canceled" : "finished",
                    DateUtils.formatElapsedTime(getDuration())
            ));
            offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), false));
            try {
                offshoot.model.commit(false);
            } catch (Exception ignore) {}
        }
    }

    private long getErrorsCount() {
        return new ArrayList<>(problems).stream().filter(event -> event.getSeverity() == RadixProblem.ESeverity.ERROR).count();
    }

    private long getWarningsCount() {
        return new ArrayList<>(problems).stream().filter(event -> event.getSeverity() == RadixProblem.ESeverity.WARNING).count();
    }

    @Override
    public AbstractTaskView createView(Consumer<ITask> cancelAction) {
        return new BuildTaskView(this, eventsTreeModel, cancelAction);
    }


    private class BuildTaskView extends TaskView {

        private final JCheckBox showWarnings = new JCheckBox(Language.get(BuildWC.class, "switch@warnings"), false) {{
            setOpaque(false);
            setFocusPainted(false);
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

            treeModel.setFilter(compilerEvent ->
                    compilerEvent.getSeverity().equals(RadixProblem.ESeverity.ERROR) || showWarnings.isSelected()
            );
            showWarnings.addItemListener(event -> {
                synchronized (eventsTreeModel) {
                    eventsTreeModel.clearEvents();
                    problems.forEach(eventsTreeModel::registerEvent);
                }
            });
            problemsStatus.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (getErrorsCount() + getWarningsCount() > 0) {
                        problemsView.setVisible(!problemsView.isVisible());
                        problemsStatusSwitch.setIcon(problemsView.isVisible() ? ICON_EXPAND : ICON_COLLAPSE);
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
                public void treeNodesInserted(TreeModelEvent event) {
                    super.treeNodesInserted(event);
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
            if (nextStatus.equals(Status.FAILED) && getErrorsCount() > 0) {
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

        private final DefaultMutableTreeNode  root = new DefaultMutableTreeNode("Problems");
        private final Map<String, Definition> defs = new HashMap<>();
        private Predicate<CompilerEvent> filter = compilerEvent -> true;

        EventTreeModel() {
            super(new DefaultMutableTreeNode("Problems"));
        }

        @Override
        public DefaultMutableTreeNode getRoot() {
            return root;
        }

        void registerEvent(CompilerEvent event) {
            if (filter.test(event)) {
                Definition def = defs.computeIfAbsent(event.getDefId(), key -> new Definition(event.getDefId(), event.getName(), event.getIcon()));
                if (def.getParent() == null) {
                    insertNodeInto(def, getRoot(), getRoot().getChildCount());
                }
                insertNodeInto(new Problem(event.getSeverity(), event.getMessage()), def, def.getChildCount());
                nodeStructureChanged(def);
            }
        }

        void clearEvents() {
            defs.clear();
            getRoot().removeAllChildren();
            nodeStructureChanged(getRoot());
        }

        void setFilter(Predicate<CompilerEvent> filter) {
             this.filter = filter;
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
