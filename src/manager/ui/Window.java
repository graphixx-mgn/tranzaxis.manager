package manager.ui;

import codex.log.Logger;
import codex.notification.NotificationService;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
import manager.Manager;

public final class Window extends JFrame implements WindowStateListener {
    
    public final JPanel upgradePanel = new JPanel();
    public final JPanel taskmgrPanel = new JPanel();
    public final JPanel loggingPanel = new JPanel();
    public final JPanel explorePanel = new JPanel();
    
    private final TrayIcon trayIcon;
    private int            prevWindowState;
    Map<String, Boolean>   prevVisibleState = new LinkedHashMap<>();
    
    public Window(String title, ImageIcon icon) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        if (icon != null) {
            setIconImage(icon.getImage());
        }
        
        setPreferredSize(new Dimension(1100, 700));
        setMinimumSize(new Dimension(700, 500));
        
        upgradePanel.setBorder(new MatteBorder(0, 0, 0, 1, Color.GRAY));
        loggingPanel.setBorder(new MatteBorder(0, 1, 0, 0, Color.GRAY));
        explorePanel.setBorder(new MatteBorder(0, 0, 1, 0, Color.GRAY));
        
        GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(explorePanel)
                )
                .addGroup(layout.createSequentialGroup()
                    .addComponent(upgradePanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(taskmgrPanel)
                    .addComponent(loggingPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                )
        );
        
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(explorePanel)
                )
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(upgradePanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(taskmgrPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(loggingPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                )
        );
        if (SystemTray.isSupported()) {
            trayIcon = new TrayIcon(getIconImage(), "TranzAxis Manager");
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setVisible(true);
                    setExtendedState(prevWindowState);
                }
            });
            NotificationService notifier = new NotificationService(trayIcon);
            notifier.setCondition(() -> {
                return getState() == JFrame.ICONIFIED || getState() == 7 || !isActive();
            });
            ServiceRegistry.getInstance().registerService(notifier);
            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException e) {
                Logger.getLogger().warn("Unable to minimize window to tray: {0}", e.getMessage());
            }
        } else {
            trayIcon = null;
        }
        addWindowStateListener(this);
    }
    
    @Override
    public void setVisible(boolean visible) {
        pack();
        setLocationRelativeTo(null);
        super.setVisible(visible);
    }
    
    public final void addUnit(AbstractUnit unit, JPanel container) {
        container.removeAll();
        container.setLayout(new BorderLayout());
        container.add(unit.getViewport(), BorderLayout.CENTER);
        unit.getViewport().setPreferredSize(container.getSize());
        unit.viewportBound();
    }

    @Override
    public void windowStateChanged(WindowEvent event) {
        boolean hideToTray = Preferences.userRoot().node(Manager.class.getSimpleName()).getBoolean("useTray", false);
        
        if (event.getNewState() == ICONIFIED || event.getNewState() == 7) {
            prevWindowState = event.getOldState();
            prevVisibleState.clear();
            Arrays.asList(Frame.getFrames()).forEach((frame) -> {
                if (frame.isDisplayable() && frame != event.getWindow()) {
                    prevVisibleState.put(frame.getName(), frame.isVisible());
                    frame.setVisible(false);
                }
            });
            if (hideToTray) {
                setVisible(false);
            }
        }
        if (event.getNewState() == MAXIMIZED_BOTH || event.getNewState() == NORMAL) {
            Arrays.asList(Frame.getFrames()).forEach((frame) -> {
                if (prevVisibleState.containsKey(frame.getName())) {
                    frame.setVisible(prevVisibleState.get(frame.getName()));
                }
            });
        }
    }
}
