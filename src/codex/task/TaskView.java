package codex.task;

import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import javax.swing.ButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;

final class TaskView extends JPanel {
    
    private final String PATTERN_NORMAL = Language.get("TaskStatus", "total@normal");
    private final String PATTERN_ERRORS = Language.get("TaskStatus", "total@errors");
    
    private final JLabel       status;
    private final JProgressBar progress;
    private final JPopupMenu   popup;
    private final JPanel       view;
    
    private final List<ITask> queue = new LinkedList<>();
    
    TaskView() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(2, 2, 2, 2));
        
        status = new JLabel();
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        status.setBorder(new EmptyBorder(0, 0, 0, 10));

        progress = new JProgressBar();
        progress.setMaximum(100);
        progress.setVisible(false);
        progress.setStringPainted(true);
        
        popup = new JPopupMenu();
        popup.setBorder(new CompoundBorder(
                new LineBorder(Color.GRAY, 1), 
                new EmptyBorder(1, 1, 1, 1)
        ));
        view = new JPanel(new GridLayout(0, 1));
        popup.add(view);

        add(status, BorderLayout.CENTER);
        add(progress, BorderLayout.EAST);
        
        addMouseListener(new MouseAdapter() {
            
            @Override
            public void mouseClicked(MouseEvent event) {
                popup.setPreferredSize(new Dimension(
                        TaskView.this.getSize().width+2, view.getPreferredSize().height+2
                ));
                if (!queue.isEmpty()) {
                    popup.show(TaskView.this, -1, -view.getPreferredSize().height-4);
                }
            }
            
        });
    }
    
    void addTask(ITask task) {
        queue.add(task);
        view.add(new TaskPresentation(task));
        refreshStatus();
        task.addListener(new ITaskListener() {

            @Override
            public void statusChanged(Status status) {
                refreshStatus();
            }

            @Override
            public void progressChanged(int percent, String description) {}
        });
    }
    
    private void refreshStatus() {
        long running  = queue.stream().filter(task -> task.getStatus() == Status.PENDING || task.getStatus() == Status.STARTED).count();
        long failed   = queue.stream().filter(task -> task.getStatus() == Status.CANCELLED || task.getStatus() == Status.FAILED).count();
        boolean ready = running + failed == 0;
        
        status.setVisible(!ready);
        progress.setVisible(!ready);
        if (ready) return;
        
        long finished = queue.stream().filter(task -> task.getStatus() == Status.FINISHED).count();
        status.setText(MessageFormat.format(failed > 0 ? PATTERN_ERRORS : PATTERN_NORMAL, running, finished, failed));
    };
    
    private final class CancelButton extends JButton {
    
        private final ImageIcon activeIcon;
        private final ImageIcon passiveIcon;

        public CancelButton() {
            super();
            this.activeIcon  = ImageUtils.getByPath("/images/endtask.png");
            this.passiveIcon = ImageUtils.grayscale(this.activeIcon);
            setIcon(passiveIcon);
            setFocusPainted(false);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorder(new EmptyBorder(0, 0, 0, 5));
            setRolloverEnabled(true);

            getModel().addChangeListener((ChangeEvent event) -> {
                ButtonModel model1 = (ButtonModel) event.getSource();
                if (model1.isRollover()) {
                    setIcon(activeIcon);
                } else {
                    setIcon(passiveIcon);
                }
            });
        }
        
    }
    
}
