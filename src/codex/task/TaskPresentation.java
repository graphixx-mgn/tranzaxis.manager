package codex.task;

import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import javax.swing.ButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;

final class TaskPresentation extends JPanel {
    
    private final JLabel title;
    private final JLabel status;
    private final JProgressBar progress;
    
    TaskPresentation(ITask task) {
        super(new BorderLayout());        
        setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), 
            new EmptyBorder(new Insets(5, 5, 5, 5))
        ));
        
        title  = new JLabel(task.getTitle(), task.getStatus().getIcon(), SwingConstants.LEFT);
        status = new JLabel(task.getStatus().getDescription());
        status.setForeground(Color.GRAY);
        status.setBorder(new EmptyBorder(3, 0, 0, 0));
        
        CancelButton cancelBtn = new CancelButton();
        cancelBtn.setBorder(new EmptyBorder(0, 10, 0, 0));
        cancelBtn.addActionListener(((event) -> {
            task.cancel(true);
        }));
        
        progress = new JProgressBar();
        progress.setStringPainted(false);
        
        JPanel controls = new JPanel(new BorderLayout());
        controls.add(progress, BorderLayout.CENTER);
        controls.add(cancelBtn, BorderLayout.EAST);
        
        add(title, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        add(status, BorderLayout.AFTER_LAST_LINE);
        
        task.addListener(new ITaskListener() {
            
            @Override
            public void statusChanged(Status newStatus) {
                title.setIcon(newStatus.getIcon());
                if (newStatus != Status.STARTED) {
                    progress.setIndeterminate(false);
                    progress.setStringPainted(true);
                }
                switch (newStatus) {
                    case STARTED:
                        status.setText(newStatus.getDescription());
                        progress.setMaximum(100);
                        progress.setIndeterminate(true);
                        break;
                    case FINISHED:
                        status.setText(newStatus.getDescription());
                        status.setForeground(Color.decode("#4CAE32"));
                        progress.setForeground(Color.decode("#4CAE32"));
                        break;
                    case CANCELLED:
                        progress.setStringPainted(false);
                        status.setText(newStatus.getDescription());
                        break;
                    case FAILED:
                        status.setForeground(Color.RED);
                        progress.setForeground(Color.decode("#D93B3B"));
                }
            }

            @Override
            public void progressChanged(int percent, String description) {
                if (progress.getValue() != percent) {
                    progress.setIndeterminate(percent == 0);
                    progress.setStringPainted(percent != 0);
                    progress.setValue(percent);
                }
                if (!status.getText().equals(description)) {
                    status.setText(description);
                }
            }
            
        });
    }
    
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
