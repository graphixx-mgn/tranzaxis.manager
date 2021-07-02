package codex.task;

import codex.component.panel.HTMLView;
import codex.log.Level;
import codex.utils.ImageUtils;
import org.bridj.util.Pair;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TaskOutput extends JPanel {

    private static final ThreadLocal<ITask>     CONTEXT = new InheritableThreadLocal<>();
    private static final Map<ITask, TaskOutput> OUTPUT_MAP = new HashMap<>();
    private static final Map<Level, Color>      COLOR_MAP = new HashMap<Level, Color>(){{
        put(Level.Debug, Color.GRAY);
        put(Level.Info,  Color.BLACK);
        put(Level.Warn,  Color.decode("#AA3333"));
        put(Level.Error, Color.decode("#FF3333"));
    }};

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
        ITask context = CONTEXT.get();
        TaskOutput output = OUTPUT_MAP.get(context);
        if (output == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String colorCode = ImageUtils.hexColor(COLOR_MAP.get(level));

            HTMLEditorKit kit = (HTMLEditorKit) output.pane.getEditorKit();
            HTMLDocument  doc = (HTMLDocument) output.pane.getDocument();
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
                        .setContents(
                                new StringSelection(
                                        pane.getSelectedText().replaceAll("\\u00a0"," ")
                                        ),
                                null
                        );
            }
        });
    }};

    private TaskOutput() {
        super(new BorderLayout());

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(pane);
        scrollPane.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
        add(scrollPane, BorderLayout.CENTER);
    }

    abstract public static class ExecPhase<R> {

        protected final String description;

        public ExecPhase(String description) {
            this.description = description;
        }

        protected abstract Pair<String, R> execute() throws Exception;

        public final R process() throws Exception {
            try {
                Pair<String, R> result = execute();
                TaskOutput.put(Level.Debug, fillStepResult(description, result.getKey(), null));
                return result.getValue();
            } catch (Exception e) {
                TaskOutput.put(Level.Debug, fillStepResult(description, null, e));
                throw e;
            }
        }

        private static String RC_SUCCESS = "<font color='green'>&#x2713;</font>";
        private static String RC_ERROR   = "<font color='red'>&#x26D4;</font>";
        private static Function<String, String> FMT_SUCCESS  = input -> String.join("", Collections.nCopies(68-input.length(), ".")).concat(RC_SUCCESS);
        private static Function<String, String>  FMT_ERROR   = input -> String.join("", Collections.nCopies(68-input.length(), ".")).concat(RC_ERROR);
        private static Function<String, Integer> HTML_LENGTH = input -> input.replaceAll("\\<[^>]*>","").length();

        private static String fillStepResult(String step, String result, Throwable error) {
            return new StringBuilder(" &bull; ")
                    .append(step)
                    .append(error  == null ? (
                                    result == null ?
                                            FMT_SUCCESS.apply(step) :
                                            String
                                                    .join("", Collections.nCopies(69-step.length()-HTML_LENGTH.apply(result), "."))
                                                    .concat(result)
                            ) : (
                                    error.getMessage() == null ?
                                            FMT_ERROR.apply(step) :
                                            String
                                                    .join("", Collections.nCopies(68-step.length(), "."))
                                                    .concat(MessageFormat.format(
                                                            "<font color='red'>&#x26D4;</font><br/>   <font color='maroon'>{0}: {1}</font>",
                                                            error.getClass().getCanonicalName(),
                                                            error.getMessage()
                                                    ))
                            )
                    ).toString();
        }
    }
}
