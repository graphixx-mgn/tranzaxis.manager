package codex.log;

import codex.component.button.PushButton;
import codex.component.button.ToggleButton;
import codex.notification.NotificationService;
import codex.service.AbstractService;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BoundedRangeModel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.varia.LevelMatchFilter;

public class LogUnit extends AbstractUnit implements WindowStateListener {
    
    final static ImageIcon NONE  = ImageUtils.grayscale(ImageUtils.getByPath("/images/log.png"));
    final static ImageIcon DEBUG = ImageUtils.getByPath("/images/debug.png");
    final static ImageIcon INFO  = ImageUtils.getByPath("/images/event.png");
    final static ImageIcon WARN  = ImageUtils.getByPath("/images/warn.png");
    final static ImageIcon ERROR = ImageUtils.getByPath("/images/stop.png");
    
    private final JFrame     frame;
    private boolean          frameState;
    private int              maxLevel = Level.ALL_INT;
    private boolean          autoScroll = true;
    private TextPaneAppender paneAppender;
    private Map<Level, ImageIcon>          levelIcon = new HashMap<>();
    private final Map<Level, ToggleButton> filterButtons;
    
    public LogUnit() {
        JTextPane logPane = new JTextPane();
        paneAppender = new TextPaneAppender(logPane);
        Logger.getLogger().addAppender(paneAppender);
        Logger.getLogger().debug("Initialize unit: Logger");
        
        levelIcon.put(Level.ALL,   NONE);
        levelIcon.put(Level.DEBUG, DEBUG);
        levelIcon.put(Level.INFO,  INFO);
        levelIcon.put(Level.WARN,  WARN);
        levelIcon.put(Level.ERROR, ERROR);
        
        frame = new JFrame();
        frame.setTitle(Language.get("title"));
        frame.setIconImage(ImageUtils.getByPath("/images/log.png").getImage());
        frame.pack();
        
        Box toolBar = new Box(BoxLayout.X_AXIS);
        toolBar.setBorder(new EmptyBorder(6, 6, 3, 6));
        filterButtons = createFilter(Level.DEBUG, Level.INFO, Level.WARN);
        filterButtons.forEach((level, button) -> {
            toolBar.add(button);
            toolBar.add(Box.createHorizontalStrut(2));            
        });       
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setMaximumSize(new Dimension(1, 30));
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(sep, BorderLayout.LINE_START);
        toolBar.add(Box.createHorizontalStrut(5));
        PushButton clear = new PushButton(ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 26, 26), null);
        toolBar.add(clear);
        
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logPane.setEditable(false);
        logPane.setBorder(new EmptyBorder(0, 6, 0, 6));
        
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(3, 3, 3, 3), 
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(logPane);
        
        GroupLayout layout = new javax.swing.GroupLayout(frame.getContentPane());
        frame.getContentPane().setLayout(layout);
        
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(scrollPane, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 959, Short.MAX_VALUE)
            .addComponent(toolBar)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(toolBar, 40, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 535, Short.MAX_VALUE))
        );
        paneAppender.addFilter(new Filter() {

            @Override
            public int decide(LoggingEvent event) {
                if (event.getLevel().toInt() > maxLevel) {
                    ((JButton) getViewport()).setIcon(ImageUtils.resize(levelIcon.get(event.getLevel()), 17, 17));
                    maxLevel = event.getLevel().toInt();
                }
                return Filter.ACCEPT;
            }
        });
        clear.addActionListener((ActionEvent event) -> {
           logPane.setText("");
           maxLevel = Level.ALL_INT;
           ((JButton) view).setIcon(ImageUtils.resize(NONE, 17, 17));
        });
        
        scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            BoundedRangeModel brm = scrollPane.getVerticalScrollBar().getModel();
            
            @Override
            public void adjustmentValueChanged(AdjustmentEvent event) {
                if (!brm.getValueIsAdjusting()) {
                    if (autoScroll) brm.setValue(brm.getMaximum());
                } else {
                    autoScroll = (brm.getValue() + brm.getExtent()) == brm.getMaximum();
                }
            }
        });
        scrollPane.addMouseWheelListener(new MouseWheelListener() {
            BoundedRangeModel brm = scrollPane.getVerticalScrollBar().getModel();
            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (event.getWheelRotation() < 0) {
                    autoScroll = false;
                } else {
                    autoScroll = (brm.getValue() + brm.getExtent()) == brm.getMaximum();
                }
            }
        });
        ServiceRegistry.getInstance().registerService(new LogManagementService(), false);
    }

    @Override
    public JComponent createViewport() {
        JButton button = new JButton(Language.get("title"));
        button.setIcon(ImageUtils.resize(NONE, 17, 17));
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(0, 5, 0, 5));
        button.addActionListener((ActionEvent event) -> {
            frame.setVisible(!frame.isVisible());
        });
        return button;
    }

    @Override
    public void viewportBound() {
        final GraphicsEnvironment graphEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice[] graphDevs = graphEnv.getScreenDevices();
        if (graphDevs.length > 1) {
            Logger.getLogger().info("Detected multi screens configuration. EventLog window moved to 2ND device");
            final Insets scnMax = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
            int taskBarSize = scnMax.bottom;
            final Rectangle bounds = graphDevs[1].getDefaultConfiguration().getBounds();
            frame.setSize(bounds.width, bounds.height-taskBarSize);
            frame.setLocation(bounds.x, bounds.y);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            Logger.getLogger().log(Level.INFO, "Detected single screen configuration. Event Log opened at center of 1ST device");
            frame.setSize(new Dimension(1000, 600));
            frame.setMinimumSize(new Dimension(500, 400));  
            frame.setLocationRelativeTo(null);
        }
        SwingUtilities.getWindowAncestor(view).addWindowStateListener(this);
    }
    
    @Override
    public void windowStateChanged(WindowEvent event) {
        if (event.getNewState() == JFrame.ICONIFIED) {
            frameState = frame.isVisible();
            frame.setVisible(false);
        } else {
            frame.setVisible(frameState);
        }
    }
    
    private Map<Level, ToggleButton> createFilter(Level... levels) {
        Map<Level, ToggleButton> switches = new LinkedHashMap<>();
        for (final Level level : levels) {
            final ToggleButton toggle = new ToggleButton(
                    ImageUtils.resize(levelIcon.get(level), 22, 22), 
                    Language.get("level@"+level.toString().toLowerCase()+".title"), 
                    true
            );
            toggle.addActionListener((ActionEvent event) -> {
                paneAppender.toggleLevel(level, toggle.isChecked());
            });
            switches.put(level, toggle);
        }
        return switches;
    }
    
    public class LogManagementService extends AbstractService<LoggerServiceOptions> implements ILogManagementService {
        
        @Override
        public boolean isStoppable() {
            return false;
        }

        @Override
        public void startService() {
            super.startService();
            ServiceRegistry.getInstance().addRegistryListener(NotificationService.class, (service) -> {
                ((NotificationService) service).registerSource(Logger.NS_SOURCE);
            });
        }
        
        @Override
        public void changeLevels(Map<codex.log.Level, Boolean> levels) {
            levels.forEach((level, enable) -> {
                if (filterButtons.containsKey(level.log4jLevel)) {
                    filterButtons.get(level.log4jLevel).setChecked(enable);
                    LogUnit.this.paneAppender.toggleLevel(level.log4jLevel, enable);
                    
                    Appender appender;
                    switch (level) {
                        case Debug:
                            appender = Logger.getRootLogger().getAppender("DEBUG");
                            break;
                        case Info:
                            appender = Logger.getRootLogger().getAppender("INFO");
                            break;
                        case Warn:
                            appender = Logger.getRootLogger().getAppender("WARN");
                            break;
                        case Error:
                            appender = Logger.getRootLogger().getAppender("ERR");
                            break;
                        default:
                            return;
                    }
                    LevelMatchFilter filter = (LevelMatchFilter) appender.getFilter();
                    while (!filter.getLevelToMatch().equals(level.getSysLevel().toString())) {
                        filter = (LevelMatchFilter) filter.getNext();
                    }
                    filter.setAcceptOnMatch(enable);
                }
            });
        }
    }
    
}
