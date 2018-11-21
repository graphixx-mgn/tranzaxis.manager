package codex.service;

import codex.unit.AbstractUnit;
import javax.swing.JComponent;
import javax.swing.JScrollPane;


/**
 * Модуль управления сервисами.
 */
public class ServiceUnit extends AbstractUnit {
    
    private JScrollPane browsePanel;

    @Override
    public JComponent createViewport() {
        browsePanel = new JScrollPane();
        browsePanel.setBorder(null);
        return browsePanel;
    }

    @Override
    public void viewportBound() {
        browsePanel.setViewportView(ServiceRegistry.getInstance().getCatalog().getSelectorPresentation());
    }
    
}
