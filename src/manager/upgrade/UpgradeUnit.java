package manager.upgrade;

import codex.instance.IInstanceListener;
import codex.instance.Instance;
import codex.instance.InstanceCommunicationService;
import codex.log.Logger;
import codex.notification.NotificationService;
import codex.notification.NotifyCondition;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Font;
import java.awt.Insets;
import java.awt.TrayIcon;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import manager.xml.Version;

public final class UpgradeUnit extends AbstractUnit implements IInstanceListener {
    
    private final static ImageIcon ICON = ImageUtils.resize(ImageUtils.getByPath("/images/upgrade.png"), 17, 17);
    private final static InstanceCommunicationService ICS = (InstanceCommunicationService) ServiceRegistry.getInstance().lookupService(InstanceCommunicationService.class);
    private final static NotificationService          NSS = (NotificationService) ServiceRegistry.getInstance().lookupService(NotificationService.class);
    final static String  NS_SOURCE = "Upgrade/New version";
    
    private Version currentVersion;
    
    public UpgradeUnit() {
        Logger.getLogger().debug("Initialize unit: Upgrade Manager");
        ICS.addInstanceListener(this);
        try {
            currentVersion = ((IUpgradeService) ICS.getService(UpgradeService.class)).getCurrentVersion();
        } catch (NotBoundException e) {}
        NSS.registerSource(NS_SOURCE, NotifyCondition.ALWAYS);
    }

    @Override
    public JComponent createViewport() {
        JButton button = new JButton(MessageFormat.format(Language.get("current"), currentVersion.getNumber()));
        button.setIcon(ICON);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(0, 5, 0, 5));
        
        button.addActionListener((e) -> {
            
        });
        return button;
    }

    @Override
    public void instanceLinked(Instance instance) {
        try {
            IUpgradeService remoteUpService = (IUpgradeService) instance.getService(UpgradeService.class);
            JButton button = (JButton) getViewport();
            Version availVersion = remoteUpService.getCurrentVersion();
            button.setText(MessageFormat.format(Language.get("available"), availVersion.getNumber()));
            button.setFont(button.getFont().deriveFont(Font.BOLD));
            
            NSS.showMessage(
                    NS_SOURCE, 
                    Language.get("notify@title"), 
                    MessageFormat.format(Language.get("notify@message"), availVersion.getNumber()),
                    TrayIcon.MessageType.INFO
            );
        } catch (NotBoundException | RemoteException e) {
            e.printStackTrace();
        }
    }
    
}
