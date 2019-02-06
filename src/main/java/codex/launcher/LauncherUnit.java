package codex.launcher;

import codex.component.border.DashBorder;
import codex.component.border.RoundedBorder;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.log.Logger;
import codex.model.Entity;
import codex.presentation.CommandPanel;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.unit.AbstractUnit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * Панель быстрого запуска, отображается до выбора элементов дерева проводника.
 */
public final class LauncherUnit extends AbstractUnit {

    private final static IConfigStoreService  CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static ITaskExecutorService TES = (ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class);
    
    private final SectionContainer shortcutPanel = new SectionContainer();
    private final DragHandler      dragHandler = new DragHandler();

    @Override
    public JComponent createViewport() {
        JPanel panel = new JPanel(new BorderLayout());
        
        CreateSection createSection = new CreateSection() {
            @Override
            void boundView(ShortcutSection section) {
                addSection(section);
            }
        };
        CreateShortcut createShortcut = new CreateShortcut(createSection) {
            @Override
            void boundView(Shortcut shortcut) {
                addShortcut(shortcut.getSection(), shortcut);
            }
        };
        CommandPanel commandPanel = new CommandPanel(createSection, createShortcut);
        panel.add(commandPanel, BorderLayout.NORTH);
        
        JScrollPane scrollPane = new JScrollPane(shortcutPanel);        
        scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        scrollPane.setColumnHeader(null);
          
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
    
    @Override
    public void viewportBound() {
        if (CAS.readCatalogEntries(null, ShortcutSection.class).isEmpty()) {
            try {
                CAS.initClassInstance(ShortcutSection.class, ShortcutSection.DEFAULT, new HashMap<>(), null);
            } catch (Exception e) {
                Logger.getLogger().error("Unable to create default section", e);
            }
        }
        TES.quietTask(new LoadSections());
    }
    
    private void addSection(ShortcutSection section) {
        if (section.getPID().equals(ShortcutSection.DEFAULT)) {
            shortcutPanel.add(section.getView(), 0);
        } else {
            shortcutPanel.add(section.getView());
        }
    }
    
    private void addShortcut(ShortcutSection section, Shortcut shortcut) {
        LaunchShortcut launcher = section.addShortcut(shortcut);
        launcher.addMouseListener(dragHandler);
        launcher.addMouseMotionListener(dragHandler);
    }
    
    
    private class LoadSections extends AbstractTask<Void> {

        private LoadSections() {
            super("Load sections");
        }

        @Override
        public Void execute() throws Exception {
            Map<Integer, Map<String, String>> shortcutData = CAS.readCatalogEntries(null, Shortcut.class).entrySet().stream()
                    .collect(
                            LinkedHashMap::new,
                            (map, entry) -> map.put(entry.getKey(), CAS.readClassInstance(Shortcut.class, entry.getKey())),
                            Map::putAll
                    );
            CAS.readCatalogEntries(null, ShortcutSection.class).forEach((ID, PID) -> {
                ShortcutSection section = (ShortcutSection) Entity.newInstance(ShortcutSection.class, null, PID);
                addSection(section);
                shortcutData.values().stream()
                        .filter((values) -> {
                            return  section.getID().toString().equals(values.get("section")) || (
                                        values.get("section") == null && section.getPID().equals(ShortcutSection.DEFAULT)
                                    );
                        }).forEach((values) -> {
                            addShortcut(
                                    section,
                                    (Shortcut) Entity.newInstance(Shortcut.class, null, values.get("PID"))
                            );
                        });
            });
            return null;
        }

        @Override
        public void finished(Void result) {}
    
    }
    
    
    private static final int DRAG_TRESHOLD = 25;
    private class DragHandler extends MouseAdapter {

        private final Box cursor;
        private LaunchShortcut sourceLauncher;
        private JFrame         ghostLauncher;
        private Point          dragStartPoint;
        private boolean        dragEnabled = false;
        
        private DragHandler() {
            cursor = Box.createHorizontalBox();
            cursor.setBackground(Color.decode("#3399FF"));
            cursor.setOpaque(true);
            cursor.setPreferredSize(new Dimension(2, 80));
        }

        @Override
        public void mousePressed(MouseEvent e) {
            Point location = e.getLocationOnScreen();
            SwingUtilities.convertPointFromScreen(location, shortcutPanel);
            
            Component comp = SwingUtilities.getDeepestComponentAt(
                    shortcutPanel, 
                    location.x, location.y
            );
            if (comp != null && comp instanceof LaunchShortcut) {
                dragStartPoint = location;
                sourceLauncher = (LaunchShortcut) comp;
                
                ghostLauncher = new JFrame();
                ghostLauncher.setUndecorated(true);
                ghostLauncher.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                
                ghostLauncher.setBackground(new Color(0, 0, 0, 0));
                ghostLauncher.setOpacity(0.6f);
                ghostLauncher.getContentPane().add(new LaunchButton(null) {
                    {
                        setBorder(new RoundedBorder(new DashBorder(Color.GRAY, 5, 1), 18));
                        setIcon(sourceLauncher.getIcon());
                        setText(sourceLauncher.getText());
                    }
                    @Override
                    protected void stateChanged() {}
                });
                ghostLauncher.setPreferredSize(new Dimension(110, 90));
                ghostLauncher.pack();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            Point location = e.getLocationOnScreen();
            SwingUtilities.convertPointFromScreen(location, shortcutPanel);
            
            double distance = Math.sqrt(Math.pow(location.x - dragStartPoint.x, 2) + Math.pow(location.y - dragStartPoint.y, 2));
            if (!dragEnabled && distance < DRAG_TRESHOLD) return;
            
            dragEnabled = true;
            sourceLauncher.setEnabled(false);
            sourceLauncher.setBorder(new RoundedBorder(new DashBorder(Color.RED, 5, 1), 18));
            ghostLauncher.setVisible(true);
            ghostLauncher.setLocation(e.getLocationOnScreen());

            Component comp = SwingUtilities.getDeepestComponentAt(
                    shortcutPanel, 
                    location.x, location.y
            );
            if (comp != null) {
                ShortcutContainer container = null;
                if (comp instanceof ShortcutContainer) {
                    container = (ShortcutContainer) comp;
                }
                if (comp instanceof LaunchShortcut) {
                    container = (ShortcutContainer) comp.getParent();
                }
                if (container != null) {
                    moveCursor(
                            container, 
                            getCurrentCursorPosition(container), 
                            calcNextCursorPosition(container, e.getLocationOnScreen())
                    );
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragEnabled = false;
            if (sourceLauncher != null) {
                sourceLauncher.setEnabled(true);
                sourceLauncher.stateChanged();
            }
            if (ghostLauncher.isVisible()) {
                ghostLauncher.dispose();
            }
            if (cursor.getParent() != null) {
                sourceLauncher.getParent().remove(sourceLauncher);
                int cursorPos = cursor.getParent().getComponentZOrder(cursor);
                ShortcutSection section = ((ShortcutContainer) cursor.getParent()).getSection();
                cursor.getParent().remove(cursor);
                section.addLauncher(sourceLauncher, cursorPos);  
            }
        }
        
        private int getCurrentCursorPosition(Container parent) {
            for (int index = 0; index < parent.getComponentCount(); index++) {
                if (parent.getComponent(index) == cursor) {
                    return index;
                }
            }
            return -1;
        }
        
        private int calcNextCursorPosition(Container container, Point screenLocation) {
            Point location = screenLocation.getLocation();
            SwingUtilities.convertPointFromScreen(location, container);
            
            int newPos;
            if (container.getComponentCount() == 0) {
                newPos = 0;
            } else {
                newPos = 0;
                int launchIdx = -1;
                for (Component child : container.getComponents()) {
                    if (child instanceof LaunchShortcut) {
                        launchIdx++;
                        
                        Point middlePoint = child.getLocation();
                        middlePoint.translate(child.getBounds().width / 2, 0);
                        if (location.x > middlePoint.x) {
                            newPos = launchIdx+1;
                        }
                    }
                }
            }
            return newPos;
        }
        
        private void moveCursor(Container container, int prevPosition, int nextPosition) {
            if (prevPosition != nextPosition) {
                container.add(cursor, nextPosition);
            }
        }
    }
    
}
