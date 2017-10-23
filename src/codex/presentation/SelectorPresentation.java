package codex.presentation;

import java.awt.Color;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

/**
 * Презентация селектора сущности. Реализует как функциональность отображения и 
 * редактирования дочерних сущностей, так и обеспечивает работу команд по созданию
 * новых сущностей.
 */
public final class SelectorPresentation extends JPanel {
    
    /**
     * Конструктор презентации. 
     */
    public SelectorPresentation() {
        setBorder(new LineBorder(Color.RED, 1));
    }
    
}
