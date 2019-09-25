package manager.upgrade;

import codex.instance.RemoteHost;
import codex.service.RemoteServiceControl;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.xml.Version;
import javax.swing.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;

public class UpgradeServiceControl extends RemoteServiceControl<IUpgradeService> {

    private final static ImageIcon ICON_INFO = ImageUtils.getByPath("/images/info.png");

    public UpgradeServiceControl(EntityRef<RemoteHost> owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/upgrade.png"));
    }

    @Override
    public Iconified getServiceInfo() {
        try {
            final Version version = getService().getCurrentVersion();
            if (version != null) {
                return new Iconified() {
                    @Override
                    public ImageIcon getIcon() {
                        return ICON_INFO;
                    }

                    @Override
                    public String toString() {
                        return MessageFormat.format(
                                Language.get(UpgradeUnit.class, "current"),
                                version.getNumber(), version.getDate()
                        );
                    }
                };
            }
        } catch (RemoteException | NotBoundException e) {
            serviceCallFault(e);
        }
        return null;
    }

}
