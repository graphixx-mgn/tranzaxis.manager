package manager.upgrade;

import codex.component.dialog.Dialog;
import codex.instance.IInstanceListener;
import codex.instance.Instance;
import codex.instance.InstanceCommunicationService;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Insets;
import java.net.InetSocketAddress;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.ServerException;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import manager.xml.Version;
import manager.xml.VersionsDocument;

public final class UpgradeUnit extends AbstractUnit implements IInstanceListener {
    
    private final static ImageIcon ICON = ImageUtils.resize(ImageUtils.getByPath("/images/upgrade.png"), 17, 17);
    private final static InstanceCommunicationService ICS = (InstanceCommunicationService) ServiceRegistry.getInstance().lookupService(InstanceCommunicationService.class);
    
    private final Version currentVersion;
    private final AtomicBoolean skipUpgrade = new AtomicBoolean(false);
    
    public UpgradeUnit() {
        Logger.getLogger().debug("Initialize unit: Upgrade Manager");
        ICS.addInstanceListener(this);
        currentVersion = UpgradeService.getVersion();
    }

    @Override
    public JComponent createViewport() {
        return new JLabel(
                MessageFormat.format(Language.get("current"), currentVersion.getNumber()), 
                ICON, SwingConstants.CENTER
        ) {{
            setBorder(new EmptyBorder(new Insets(2, 10, 2, 10)));
        }};
    }

    @Override
    public void instanceLinked(Instance instance) {
        new Thread(() -> {
            synchronized (skipUpgrade) {
                if (skipUpgrade.get()) return;
                try {
                    IUpgradeService remoteUpService = (IUpgradeService) instance.getService(UpgradeService.class);
                    Logger.getLogger().debug("Check remote instance {0} for upgrade", instance);
                    Version availVersion = remoteUpService.getCurrentVersion();
                    if (UpgradeService.VER_COMPARATOR.compare(currentVersion, availVersion) < 0) {
                        Logger.getLogger().debug("Available upgrade {0} => {1}", currentVersion.getNumber(), availVersion.getNumber());
                        VersionsDocument diff = remoteUpService.getDiffVersions(currentVersion, availVersion);
                        new UpgradeDialog(instance, diff, (event) -> {
                            skipUpgrade.set(event.getID() != Dialog.OK);
                            if (event.getID() == Dialog.OK) {
                                upgrade(instance);
                            }
                        }).setVisible(true);
                    } else {
                        Logger.getLogger().debug("Upgrade is not available. Remote instance version: {0}", availVersion.getNumber());
                    }
                } catch (ConnectException | NotBoundException e) {
                    // Do nothing
                } catch (ServerException e) {
                    Logger.getLogger().warn("Remote service call error: {0}", e.getCause().getMessage());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    private void upgrade(Instance instance) {
        InetSocketAddress rmiAddress = instance.getRemoteAddress();
        new Updater(
                rmiAddress.getAddress().getHostAddress(),
                rmiAddress.getPort()
        ).start();
    }
    
}
