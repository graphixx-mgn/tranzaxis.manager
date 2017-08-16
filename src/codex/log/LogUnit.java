package codex.log;

import codex.component.button.PushButton;
import codex.component.button.ToggleButton;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
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
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.apache.log4j.Level;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

public class LogUnit extends AbstractUnit implements WindowStateListener {
    
    private final static ImageIcon none  = ImageUtils.grayscale(ImageUtils.getByPath("/images/log.png"));
    private final static ImageIcon debug = ImageUtils.getByPath("/images/debug.png");
    private final static ImageIcon info  = ImageUtils.getByPath("/images/event.png");
    private final static ImageIcon warn  = ImageUtils.getByPath("/images/warn.png");
    private final static ImageIcon error = ImageUtils.getByPath("/images/stop.png");
    
    private final JFrame frame;
    private boolean frameState;
    private TextPaneAppender paneAppender;
    private int maxLevel = Level.ALL_INT;
    private Map<Level, ImageIcon> levelIcon = new HashMap<>();
    private Map<Level, String>    levelDesc = new HashMap<>();
    
    public LogUnit() {
        levelIcon.put(Level.ALL,   none);
        levelIcon.put(Level.DEBUG, debug); levelDesc.put(Level.DEBUG, "Debug");
        levelIcon.put(Level.INFO,  info);  levelDesc.put(Level.INFO,  "Event");
        levelIcon.put(Level.WARN,  warn);  levelDesc.put(Level.WARN,  "Warning");
        levelIcon.put(Level.ERROR, error); levelDesc.put(Level.ERROR, "Error");
        
        frame = new JFrame();
        frame.setTitle("Event Log");
        frame.setIconImage(ImageUtils.getByPath("/images/log.png").getImage());
        frame.pack();
        
        Box toolBar = new Box(BoxLayout.X_AXIS);
        toolBar.setBorder(new EmptyBorder(6, 6, 3, 6));
        createFilter(Level.DEBUG, Level.INFO, Level.WARN).stream().forEach((toggleButton) -> {
            toolBar.add(toggleButton);
            toolBar.add(Box.createHorizontalStrut(2));
        });        
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setMaximumSize(new Dimension(1, 30));
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(sep, BorderLayout.LINE_START);
        toolBar.add(Box.createHorizontalStrut(5));
        PushButton clear = new PushButton(ImageUtils.getByPath("/images/remove.png"), null);
        toolBar.add(clear);
        
        JTextPane logPane = new JTextPane();
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
        
        paneAppender = new TextPaneAppender(logPane);
        paneAppender.addFilter(new Filter() {

            @Override
            public int decide(LoggingEvent event) {
                if (event.getLevel().toInt() > maxLevel) {
                    ((JButton) view).setIcon(ImageUtils.resize(levelIcon.get(event.getLevel()), 17, 17));
                    maxLevel = event.getLevel().toInt();
                }
                return Filter.ACCEPT;
            }
        });
        Logger.getLogger().addAppender(paneAppender);
        clear.addActionListener((ActionEvent event) -> {
           logPane.setText("");
           ((JButton) view).setIcon(ImageUtils.resize(none, 17, 17));
        });
    }

    @Override
    public JComponent createViewport() {
        JButton button = new JButton("Event Log");
        button.setIcon(ImageUtils.resize(none, 17, 17));
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(0, 0, 0, 0));
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
            Logger.getLogger().log(Level.INFO, "Detected multi screens configuration. EventLog window moved to 2ND device");
            final Insets scnMax = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
            int taskBarSize = scnMax.bottom;
            final Rectangle bounds = graphDevs[1].getDefaultConfiguration().getBounds();
            frame.setSize(bounds.width, bounds.height-taskBarSize);
            frame.setLocation(bounds.x, bounds.y);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            Logger.getLogger().log(Level.INFO, "Detected single screen configuration. Event Log opened at center of 1ST device");
            //frame.setSize(new Dimension(300, 200));
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
    
    private List<ToggleButton> createFilter(Level... levels) {
        List<ToggleButton> toggles = new LinkedList<>();
        for (final Level level : levels) {
            final ToggleButton toggle = new ToggleButton(levelIcon.get(level), levelDesc.get(level), true);
            toggle.addActionListener((ActionEvent event) -> {
                paneAppender.toggleLevel(level, toggle.isChecked());
            });
            toggles.add(toggle);
        }
        return toggles;
    }
}
