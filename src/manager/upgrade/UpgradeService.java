package manager.upgrade;

import java.io.InputStream;
import manager.xml.Version;
import manager.xml.VersionsDocument;


public class UpgradeService implements IUpgradeService {
    
    private static final long serialVersionUID = 1L;
    
    private final VersionsDocument versionsDocument;
    
    public UpgradeService() throws Exception {
        final InputStream in = this.getClass().getResourceAsStream("/resource/version.xml");
        versionsDocument = VersionsDocument.Factory.parse(in);
    }

    @Override
    public Version getCurrentVersion() {
        String currentVersionNumber = versionsDocument.getVersions().getCurrent();
        for (Version version : versionsDocument.getVersions().getVersionArray()) {
            if (version.getNumber().equals(currentVersionNumber)) {
                return version;
            }
        }
        return null;
    }

}
