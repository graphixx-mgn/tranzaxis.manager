package codex.task;

import codex.log.Logger;
import codex.log.LoggerContext;
import codex.log.TextPaneAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Абстрактная реализация задачи {@link ITask}, следует использовать в качестве
 * предка при разработке пользовательских задач. Содержит весь необходииый для
 * исполнения и управления состоянием код.
 * @param <T> Тип результата возвращаемого методом {@link ITask#execute()}
 */
public abstract class AbstractTask<T> implements ITask<T> {
 
    private final String title;
    private Status  status;
    private Integer percent = 0;
    private String  description;
    private final FutureTask<T> future;
    private List<ITaskListener> listeners = new LinkedList<>();
    private final Semaphore     semaphore = new Semaphore(1, true) {
        @Override
        public void release() {
            if (availablePermits() == 0) {
                // Avoid extra releases that increases permits counter
                super.release();
            }
        }
    };
    private LocalDateTime startTime, pauseTime, stopTime;

    /**
     * Конструктор задачи.
     * @param title Наименование задачи, для показа в GUI. (cм. {@link TaskMonitor}).
     */
    public AbstractTask(final String title) {
        future = new FutureTask<T>(() -> {
            try {
                LoggerContext.enterLoggerContext(this);
                new LinkedList<>(listeners).forEach((listener) -> listener.beforeExecute(this));
                T result;
                setStatus(Status.STARTED);
                try {
                    result = execute();
                } finally {
                    new LinkedList<>(listeners).forEach((listener) -> listener.afterExecute(this));
                }
                finished(result);
            } catch (CancelException e) {
                if (isCancelled() && status != Status.CANCELLED) {
                    setStatus(Status.CANCELLED);
                }
            } catch (InterruptedException e) {
                //
            } catch (Throwable e) {
                setProgress(percent, MessageFormat.format(Status.FAILED.getDescription(), e.getLocalizedMessage()));
                setStatus(Status.FAILED);
                Logger.getLogger().error(MessageFormat.format("Error on task ''{0}'' execution", getTitle()), e);
                throw e;
            } finally {
                LoggerContext.leaveLoggerContext();
                System.gc();
            }
            return null;
        }) {       
            @Override
            protected void done() {
                if (isCancelled() && status != Status.CANCELLED) {
                    setStatus(Status.CANCELLED);
                } else if (!isCancelled() && !isFailed()) {
                    setStatus(Status.FINISHED);
                }
            }
        };
        this.status = Status.PENDING;
        this.title = title;
    }

    @Override
    public abstract T execute() throws Exception;

    @Override
    public abstract void finished(T result);

    /**
     * Метод отмены задачи.
     * @param mayInterruptIfRunning Форсировать отмену, даже если задаче уже 
     * выполняется, иначе - отменится задача только в статусе ожидания.
     */
    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        if (semaphore.availablePermits() == 0) {
            setPause(false);
        }
        return future.cancel(mayInterruptIfRunning);
    }
    
    /**
     * Возвращает признак того что задача была завершена с ошибкой.
     */
    public final boolean isFailed() {
        return status == Status.FAILED;
    }

    /**
     * Возвращает признак того что задача была отменена.
     */
    @Override
    public final boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * Возвращает признак того что задача была завершена.
     */
    @Override
    public final boolean isDone() {
        return future.isDone();
    }
    
    @Override
    public boolean isPauseable() {
        return false;
    }
    
    private Status prevStatus;
    final void setPause(boolean paused) {
        if (isPauseable()) {
            if (paused) {
                try {
                    prevStatus = getStatus();
                    setStatus(Status.PAUSED);
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    //
                }
            } else {
                setStatus(prevStatus);
                semaphore.release();
            }
        }
    }
    
    @Override
    public final void checkPaused() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            //
        } finally {
            semaphore.release();
        }
    }

    @Override
    public final T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public final T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public final String getTitle() {
        return title;
    }
    
    @Override
    public final String getDescription() {
        if ((getStatus() == Status.STARTED && description != null) || getStatus() == Status.FAILED) {
            return description;
        } else {
            return getStatus().getDescription();
        }
    }

    /**
     * Установить прогресс задачи и описание состояния на данно этапе прогресса.
     * Следует вызывать из прикладной реализации метода {@link ITask#execute()}
     * если имеется возможность определить процент готовности.
     * @param percent Процент готовности (0-100).
     * @param description Описание состояния задачи на данный момент.
     */
    @Override
    public final void setProgress(int percent, String description) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Progress value should be from 0 to 100");
        }
        this.percent = percent;
        this.description = description;
        new LinkedList<>(listeners).forEach((listener) -> {
            listener.progressChanged(this, percent, description);
        });
    }
    
    @Override
    public final Integer getProgress() {
        return percent;
    }
    
    /**
     * Возвращает общее время исполнения задачи, учитывая приостановку.
     */
    protected final long getDuration() {
        if (startTime == null) {
            return 0;
        } else {
            return Duration.between(
                    startTime,
                    status.isFinal() ? stopTime : LocalDateTime.now()
            ).toMillis();
        }
    }

    /**
     * Установить состояние задачи и вернуть предыдущее.
     * @param state Константа типа {@link Status}
     */
    void setStatus(Status state) {
        if ((this.status == Status.PENDING || this.status == Status.PAUSED) && state == Status.STARTED) {
            if (pauseTime == null) {
                startTime = LocalDateTime.now();
            } else {
                startTime = startTime.minusNanos(Duration.between(LocalDateTime.now(), pauseTime).toNanos());
            }
        } else if (this.status == Status.STARTED && state == Status.PAUSED) {
            pauseTime = LocalDateTime.now();
        }
        if (state.isFinal()) {
            stopTime = LocalDateTime.now();
        }
        Status prevStatus = this.status;
        this.status = state;
        new LinkedList<>(listeners).forEach((listener) -> listener.statusChanged(this, prevStatus, status));
    }
    
    @Override
    public final Status getStatus() {
        return status;
    }

    /**
     * Запустить исполнение задачи. Вызывается сервисом исполнения задач в {@link TaskManager}.
     */
    @Override
    public final void run() {
        future.run();
    }
    
    @Override
    public AbstractTaskView createView(Consumer<ITask> cancelAction) {
        return new TaskView(this, cancelAction);
    }

    @Override
    public final void addListener(ITaskListener listener) {
        listeners.add(listener);
    }

    @Override
    public final void removeListener(ITaskListener listener) {
        listeners.remove(listener);
    }

    public JPanel createLogPane() {
        return new LogPane(this);
    }

    private class LogPane extends JPanel {
        private LogPane (AbstractTask task) {
            super(new BorderLayout());

            JTextPane infoPane = new JTextPane();
            infoPane.setEditable(false);
            infoPane.setPreferredSize(new Dimension(450, 150));
            infoPane.setContentType("text/html");
            infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            infoPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

            JScrollPane scrollPane = new JScrollPane();
            scrollPane.setLayout(new ScrollPaneLayout());
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.getViewport().add(infoPane);
            scrollPane.setBorder(new CompoundBorder(
                    new EmptyBorder(5, 0, 0, 0),
                    new LineBorder(Color.LIGHT_GRAY, 1)
            ));
            add(scrollPane, BorderLayout.CENTER);

            final TextPaneAppender paneAppender = new TextPaneAppender(infoPane) {
                @Override
                protected void append(LoggingEvent event) {
                    Style style = infoPane.getStyle(event.getLevel().toString());
                    Color color = StyleConstants.getForeground(style);
                    String hex = "#"+Integer.toHexString(color.getRGB()).substring(2);
                    String message = MessageFormat.format(
                            "<span><font color=\"{0}\">{1}</font></span>",
                            hex,
                            getLayout().format(event)
                                    .replaceAll("\n {21}", "<br>")
                                    .replaceAll(" ", "&nbsp;")
                                    .replace("\r\n", "<br>")
                    );

                    if (LoggerContext.objectInContext(task)) {
                        HTMLDocument doc = (HTMLDocument) infoPane.getStyledDocument();
                        try {
                            doc.insertAfterEnd(doc.getCharacterElement(doc.getLength()), message);
                        } catch (BadLocationException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            paneAppender.setThreshold(Priority.INFO);
            paneAppender.setLayout(new PatternLayout("%m%n"));
//            Logger.getLogger().addAppender(paneAppender);
//
//            Style style = infoPane.getStyle(Level.INFO.toString());
//            StyleConstants.setForeground(style, Color.GRAY);
//
//            task.addListener(new ITaskListener() {
//                @Override
//                public void statusChanged(ITask task, Status status) {
//                    if (status.isFinal()) {
//                        Logger.getLogger().removeAppender(paneAppender);
//                    }
//                }
//            });
        }
    }
}
