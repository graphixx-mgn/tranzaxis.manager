package codex.explorer;

import codex.unit.AbstractUnit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

public final class ExplorerUnit extends AbstractUnit {
    
    private JTree tree;
    
    public ExplorerUnit() {

    }

    @Override
    public JComponent createViewport() {
        JSplitPane splitPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPanel.setDividerLocation(230);
        splitPanel.setDividerSize(6);
        splitPanel.setBorder(new MatteBorder(1, 0, 0, 0, Color.GRAY));

        JPanel leftPanel  = new JPanel(new BorderLayout());
        JPanel rightPanel = new JPanel();

        tree = new JTree();
        tree.setFont(new Font("Tahoma", 0, 12));
        tree.setRowHeight(22);
        tree.setBorder(new EmptyBorder(5, 10, 5, 2));
        
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        leftPanel.add(scroll, BorderLayout.CENTER);

        splitPanel.setLeftComponent(leftPanel);
        splitPanel.setRightComponent(rightPanel);
        return splitPanel;
    }
    
}
