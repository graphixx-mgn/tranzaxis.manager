package codex.task;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.panel.HTMLView;
import codex.editor.IEditor;
import codex.log.Level;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.bridj.util.Pair;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

public class TaskOutput extends JPanel {

    private static final ThreadLocal<ITask>     CONTEXT    = new InheritableThreadLocal<>();
    private static final Map<ITask, TaskOutput> OUTPUT_MAP = new HashMap<>();
    private static final Map<Level, Color>      COLOR_MAP  = new HashMap<Level, Color>(){{
        put(Level.Debug, Color.GRAY);
        put(Level.Info,  Color.BLACK);
        put(Level.Warn,  Color.decode("#AA3333"));
        put(Level.Error, Color.decode("#FF3333"));
    }};

    private final Set<Enum<?>> markers = new LinkedHashSet<>();
    public static TaskOutput createOutput(ITask task) {
        if (!OUTPUT_MAP.containsKey(task)) {
            OUTPUT_MAP.put(task, new TaskOutput());
            task.addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                    if (nextStatus.isFinal()) {
                        OUTPUT_MAP.remove(task);
                    }
                }
            });
        }
        return OUTPUT_MAP.get(task);
    }

    public static void put(Level level, String message, Object... params) {
        final ITask      context = CONTEXT.get();
        final TaskOutput output  = OUTPUT_MAP.get(context);
        if (output == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            final String colorCode = ImageUtils.hexColor(COLOR_MAP.get(level));

            final HTMLEditorKit kit = (HTMLEditorKit) output.pane.getEditorKit();
            final HTMLDocument  doc = (HTMLDocument) output.pane.getDocument();
            try {
                kit.insertHTML(
                        doc,
                        doc.getLength(),
                        MessageFormat.format(
                                "<span><font color=\"{0}\">{1}</font></span>{2}",
                                colorCode,
                                MessageFormat.format(
                                        message.replaceAll(" ", "&nbsp;").replace("\r\n", "<br>"),
                                        params
                                ),
                                doc.getLength() > 0 ? "<br>" : ""
                        ),
                        0, 0, null
                );
            } catch (BadLocationException | IOException e) {
                e.printStackTrace();
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum> Set<E> getMarkersAs(Class<E> enumClass) {
        final ITask      context = CONTEXT.get();
        final TaskOutput output  = OUTPUT_MAP.get(context);
        synchronized (output.markers) {
            return (Set<E>) new LinkedHashSet<>(output.markers);
        }
    }

    synchronized static void defineContext(ITask task) {
        CONTEXT.set(task);
    }

    synchronized static void clearContext() {
        CONTEXT.set(null);
    }

    private final HTMLView pane = new HTMLView() {{
        setDocument(new HTMLDocument());
        setOpaque(true);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        setBackground(UIManager.getDefaults().getColor("TextPane.background"));
        ((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        getActionMap().put(DefaultEditorKit.copyAction, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(pane.getSelectedText().replaceAll("\\u00a0"," ")), null);
            }
        });
    }};

    private TaskOutput() {
        super(new BorderLayout());

        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(pane);
        scrollPane.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(560, 400);
    }

    @FunctionalInterface
    public interface IPhaseCallback {
        void callback();
    }

    abstract public static class ExecPhase<R> {

        protected final String description;
        private IPhaseCallback onStart, onFinish;

        public ExecPhase(String description) {
            this.description = description;
        }

        protected abstract Pair<String, R> execute() throws Exception;

        public final ExecPhase<R> onStart(IPhaseCallback callback) {
            onStart = callback;
            return this;
        }
        public final ExecPhase<R> onFinish(IPhaseCallback callback) {
            onFinish = callback;
            return this;
        }

        public final ITask getContextTask() {
            return CONTEXT.get();
        }

        public final R process() throws Exception {
            return this.process(null, false);
        }

        public final R process(Enum<?> marker) throws Exception {
            return this.process(marker, false);
        }

        public final R process(boolean quiet) throws Exception {
            return this.process(null, quiet);
        }

        public final R process(Enum<?> marker, boolean quiet) throws Exception {
            try {
                final ITask contextTask = CONTEXT.get();
                if (!quiet) {
                    contextTask.setProgress(contextTask.getProgress(), description);
                }
                if (onStart != null) onStart.callback();

                Pair<String, R> result = execute();
                if (marker != null) {
                    final TaskOutput output = OUTPUT_MAP.get(contextTask);
                    synchronized (output.markers) {
                        output.markers.add(marker);
                    }
                }
                if (!quiet) {
                    TaskOutput.put(Level.Debug, fillStepResult(description, result.getKey(), null));
                }
                return result.getValue();
            } catch (Exception e) {
                if (!quiet) {
                    TaskOutput.put(Level.Debug, fillStepResult(description, null, e));
                }
                throw e;
            } finally {
                if (onFinish != null) onFinish.callback();
            }
        }

        private static String RC_SUCCESS = "<font color='green'>&#x2713;</font>";
        private static String RC_ERROR   = "<font color='red'>&#x26D4;</font>";
        private static Function<String, String>  FMT_SUCCESS = input -> String.join("", Collections.nCopies(71-input.length(), ".")).concat(RC_SUCCESS);
        private static Function<String, String>  FMT_ERROR   = input -> String.join("", Collections.nCopies(71-input.length(), ".")).concat(RC_ERROR);
        private static Function<String, Integer> HTML_CLEAN  = input -> Jsoup.clean(input, Safelist.none()).length();

        private static String fillStepResult(String step, String result, Throwable error) {
            return new StringBuilder(" &bull; ")
                    .append(step)
                    .append(error  == null ? (
                            result == null ?
                            FMT_SUCCESS.apply(step) :
                            String.join("", Collections.nCopies(72-HTML_CLEAN.apply(step)-HTML_CLEAN.apply(result), ".")).concat(result)
                        ) : (
                            error.getMessage() == null ?
                            FMT_ERROR.apply(step) :
                            String.join("", Collections.nCopies(71-step.length(), ".")).concat(MessageFormat.format(
                                    "<font color='red'>&#x26D4;</font><br/>   <font color='maroon'>{0}</font>",
                                    error.getMessage()
                            ))
                        )
                    ).toString();
        }
    }


    public abstract static class Wizard<R> extends AbstractTask<R> {

        private final ImageIcon dialogIcon;
        public Wizard(String title, ImageIcon dialogIcon) {
            super(title);
            this.dialogIcon = dialogIcon;
        }

        protected abstract R process() throws Exception;

        @Override
        public final R execute() throws Exception {
            final DialogButton dialogButton = Dialog.Default.BTN_CLOSE.newInstance();

            dialogButton.setEnabled(false);
            final Dialog dialog = new Dialog(
                    Dialog.findNearestWindow(),
                    dialogIcon,
                    Language.get(TaskMonitor.class, "dialog.title"),
                    new JPanel(new BorderLayout(0, 5)) {{
                        setBorder(new EmptyBorder(5, 5, 5, 5));
                        AbstractTaskView taskView = createView(null);
                        taskView.setBorder(new CompoundBorder(
                                new LineBorder(Color.LIGHT_GRAY, 1),
                                new EmptyBorder(5, 5, 5, 5)
                        ));
                        add(taskView, BorderLayout.NORTH);
                        add(TaskOutput.createOutput(Wizard.this), BorderLayout.CENTER);
                    }},
                    event -> {},
                    dialogButton
            ) {{
                setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                setResizable(false);

                final Function<DialogButton, ActionListener> defHandler = handler;
                handler = (button) -> (event) -> {
                    if (getDefaultCloseOperation() == WindowConstants.DO_NOTHING_ON_CLOSE) {
                        if (allowTermination()) {
                            // Interrupt task
                            cancel(true);
                            // Allow to close
                            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                            defHandler.apply(button).actionPerformed(event);
                        }
                    } else {
                        defHandler.apply(button).actionPerformed(event);
                    }
                };
            }};
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));

            try {
                return process();
            } finally {
                dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                dialogButton.setEnabled(true);
            }
        }

        protected boolean allowTermination() {
            final Semaphore lock = new Semaphore(1);
            final DialogButton submitBtn = Dialog.Default.BTN_CANCEL.newInstance(Language.get(TaskMonitor.class, "dialog@warn.cancel"));
            final JPanel warnMessage = new JPanel(new BorderLayout()) {{
                final JLabel label = new JLabel(
                        Language.get(TaskMonitor.class, "dialog@warn.mess"),
                        ImageUtils.getByPath("/images/warn.png"),
                        SwingConstants.LEFT
                );
                label.setOpaque(true);
                label.setIconTextGap(5);
                label.setBackground(new Color(0x33DE5347, true));
                label.setForeground(IEditor.COLOR_INVALID);
                label.setBorder(new CompoundBorder(
                        new LineBorder(Color.decode("#DE5347"), 1),
                        new EmptyBorder(5, 5, 5, 5)
                ));
                setBorder(new EmptyBorder(0, 0, 5, 0));
                add(label, BorderLayout.CENTER);
            }};

            final Dialog dialog = new Dialog(
                    Dialog.findNearestWindow(),
                    dialogIcon,
                    Language.get(TaskMonitor.class, "dialog@warn.title"),
                    new JPanel(new BorderLayout()) {{
                        setBorder(new EmptyBorder(5, 10, 5, 10));
                        add(warnMessage, BorderLayout.NORTH);
                    }},
                    e -> lock.release(),
                    submitBtn
            ) {
                {
                    setResizable(false);
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(500, super.getPreferredSize().height);
                }
            };
            try {
                lock.acquire();
                dialog.setVisible(true);
                lock.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return dialog.getExitCode() == Dialog.CANCEL;
        }
    }
}
