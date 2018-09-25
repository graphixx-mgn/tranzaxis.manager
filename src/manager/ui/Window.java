package manager.ui;

import codex.log.Logger;
import codex.notification.NotificationService;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
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
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import manager.Manager;

public final class Window extends JFrame implements WindowStateListener {
    
    private final static ImageIcon LAUNCH  = ImageUtils.resize(ImageUtils.getByPath("/images/launch.png"), 20, 20);
    private final static ImageIcon VIEWER  = ImageUtils.resize(ImageUtils.getByPath("/images/viewer.png"), 20, 20);
    //private final static ImageIcon SERVICE = ImageUtils.resize(ImageUtils.getByPath("/images/services.png"), 20, 20);
    
    private JTabbedPane tabbedPanel  = new JTabbedPane(JTabbedPane.LEFT);
    public final JPanel upgradePanel = new JPanel();
    public final JPanel taskmgrPanel = new JPanel();
    public final JPanel loggingPanel = new JPanel();
    public final JPanel explorePanel = new JPanel();
    public final JPanel launchPanel  = new JPanel();
    //public final JPanel servicePanel = new JPanel();
    
    private final TrayIcon  trayIcon;
    private int             prevWindowState;
    Map<String, Boolean>    prevVisibleState = new LinkedHashMap<>();
    
    public Window(String title, ImageIcon icon) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        if (icon != null) {
            setIconImage(icon.getImage());
        }
        
        setPreferredSize(new Dimension(1200, 800));
        setMinimumSize(new Dimension(900, 500));
        
        tabbedPanel.setFocusable(false);
        tabbedPanel.setBorder(new MatteBorder(1, 0, 1, 0, Color.GRAY));
        upgradePanel.setBorder(new MatteBorder(0, 0, 0, 1, Color.GRAY));
        loggingPanel.setBorder(new MatteBorder(0, 1, 0, 0, Color.GRAY));
        
        tabbedPanel.addTab(null, VIEWER,  explorePanel);
        tabbedPanel.addTab(null, LAUNCH,  launchPanel);
//        tabbedPanel.addTab(null, SERVICE, servicePanel);
        
        GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(tabbedPanel)
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
                    .addComponent(tabbedPanel)
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
    
    public void restoreState() {
        if (getState() == JFrame.ICONIFIED || getState() == 7 || !isActive()) {
            setVisible(true);
            setExtendedState(prevWindowState);
            setAlwaysOnTop(true);
            toFront();
            requestFocus();
            setAlwaysOnTop(false);
        }
    }
    
    @Override
    public void setVisible(boolean visible) {
        pack();
        setLocationRelativeTo(null);
        super.setVisible(visible);
    }
    
    public final void addUnit(AbstractUnit unit, Container container) {
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
                    SwingUtilities.invokeLater(() -> {
                        frame.setVisible(prevVisibleState.get(frame.getName()));
                    });
                }
            });
        }
    }
}
