package codex.task;

import codex.component.ui.StripedProgressBarUI;
import codex.utils.Language;
import codex.utils.Runtime;
import org.bridj.Pointer;
import org.bridj.PointerIO;
import org.bridj.cpp.com.COMRuntime;
import org.bridj.cpp.com.shell.ITaskbarList3;
import org.bridj.jawt.JAWTUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;

/**
 * Виджет модуля {@link TaskManager}, представляет собой панель задач с информацией о 
 * количестве исполняемых в данный момент задач, а также из статусы и общий прогресс.
 * Скрывается, если задач нет или все выполнены успешно. При нажатии вызывается 
 * окно просмотра задач {@link TaskMonitor}.
 */
final class TaskStatusBar extends JPanel implements TaskMonitor.ITaskMonitorListener {
    
    private final String PATTERN_NORMAL = Language.get(Status.class, "total@normal");
    private final String PATTERN_ERRORS = Language.get(Status.class, "total@errors");
    
    private final JLabel       status;
    private final JProgressBar progress;
    private final ClearButton  clear;
    private final TaskMonitor  monitor = new TaskMonitor(this);
    
    /**
     * Конструктор виджета.
     */
    TaskStatusBar() {
        super(new BorderLayout());
        setVisible(false);
        setBorder(new EmptyBorder(1, 2, 1, 2));

        status = new JLabel();
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        status.setBorder(new EmptyBorder(0, 0, 0, 10));
        
        progress = new JProgressBar();
        progress.setMaximum(100);
        progress.setStringPainted(true);
        progress.setUI(new StripedProgressBarUI(true));
        progress.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));

        monitor.addMonitorListener(this);
        
        clear = new ClearButton();
        // Глобальный перехватчик событий
        // Иначе при первом клике закрывается монитор
        Toolkit.getDefaultToolkit().addAWTEventListener((AWTEvent event) -> {
            MouseEvent mouseEvent = (MouseEvent) event;
            if (event.getSource() == clear && mouseEvent.getID() == MouseEvent.MOUSE_CLICKED && clear.isEnabled()) {
                monitor.clearRegistry();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(progress, BorderLayout.CENTER);
        controls.add(clear, BorderLayout.EAST);
        
        add(status, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                monitor.setVisible(!monitor.isVisible());
            }
        });
    }

    TaskMonitor getMonitor() {
        return monitor;
    }

    @Override
    public void statusChanged(int count, long running, long stopped, long failed, int percent) {
        setVisible(running + stopped > 0);
        int prevProgress = progress.getValue();
        if (prevProgress != percent) {
            progress.setValue(percent);
            if (count == 0) {
                notifyTaskBar(ITaskbarList3.TbpFlag.TBPF_NOPROGRESS, 0);
            } else if (failed > 0 ) {
                notifyTaskBar(ITaskbarList3.TbpFlag.TBPF_ERROR, percent);
            } else {
                notifyTaskBar(ITaskbarList3.TbpFlag.TBPF_NORMAL, percent);
            }
        }
        clear.setEnabled(running == 0);
        status.setText(MessageFormat.format(stopped > 0 ? PATTERN_ERRORS : PATTERN_NORMAL, running, count - running - stopped, stopped));
    }

    @SuppressWarnings("unchecked")
    private void notifyTaskBar(ITaskbarList3.TbpFlag mode, int percent) {
        if (Runtime.OS.win7.get() && SwingUtilities.getWindowAncestor(this) != null && SwingUtilities.getWindowAncestor(this).isShowing()) {
            try {
                ITaskbarList3 taskBarIcon = COMRuntime.newInstance(ITaskbarList3.class);
                long hwndVal = JAWTUtils.getNativePeerHandle(SwingUtilities.getWindowAncestor(this));
                Pointer<?> HWND = Pointer.pointerToAddress(hwndVal, (PointerIO) null);
                taskBarIcon.SetProgressState((Pointer) HWND, mode);
                if (mode != ITaskbarList3.TbpFlag.TBPF_NOPROGRESS) {
                    taskBarIcon.SetProgressValue((Pointer) HWND, percent, 100);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    
}
