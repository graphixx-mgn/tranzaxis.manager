package manager.ui;

import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.MatteBorder;

public final class Window extends JFrame {
    
    private final JTabbedPane tabbedPanel;
    public final JPanel upgradePanel = new JPanel();
    public final JPanel taskmgrPanel = new JPanel();
    public final JPanel loggingPanel = new JPanel();
    
    public Window(String title, ImageIcon icon) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        if (icon != null) {
            setIconImage(icon.getImage());
        }
        setPreferredSize(new Dimension(1200, 800));
        setMinimumSize(new Dimension(900, 500));
        
        Insets current = UIManager.getInsets("TabbedPane.tabInsets");
        UIManager.put("TabbedPane.tabInsets", new Insets(current.top+2, current.left, current.bottom+2, current.right));
        tabbedPanel = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPanel.setFocusable(false);
        tabbedPanel .setBorder(new MatteBorder(1, 0, 1, 0, Color.GRAY));
        upgradePanel.setBorder(new MatteBorder(0, 0, 0, 1, Color.GRAY));
        loggingPanel.setBorder(new MatteBorder(0, 1, 0, 0, Color.GRAY));
        UIManager.put("TabbedPane.tabInsets", current);
        
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

    public final void addUnit(AbstractUnit unit) {
        tabbedPanel.addTab(
                null,
                ImageUtils.resize(unit.getIcon(), 20, 20),
                new JPanel(new BorderLayout()) {{
                    add(unit.getViewport(), BorderLayout.CENTER);
                    unit.getViewport().setPreferredSize(getSize());
                }},
                unit.getTitle()
        );
        unit.viewportBound();
    }
}
