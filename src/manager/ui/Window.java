package manager.ui;

import codex.unit.AbstractUnit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;

public final class Window extends JFrame {
    
    public final JPanel upgradePanel = new JPanel();
    public final JPanel taskmgrPanel = new JPanel();
    public final JPanel loggingPanel = new JPanel();
    public final JPanel explorePanel = new JPanel();
    
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
                    .addComponent(upgradePanel, 100, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(taskmgrPanel)
                    .addComponent(loggingPanel, 100, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                )
        );
        
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(explorePanel)
                )
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(upgradePanel, 22, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(taskmgrPanel, 22, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(loggingPanel, 22, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                )
        );
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
}
