package manager.upgrade;

import codex.instance.InstanceCommunicationService;
import codex.instance.MultihomeRMIClientSocketFactory;
import codex.instance.ServiceNotLoadedException;
import codex.log.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;
import manager.commands.offshoot.BuildWC;
import manager.upgrade.stream.RemoteInputStream;
import manager.upgrade.stream.RemoteInputStreamServer;
import manager.xml.Version;
import manager.xml.VersionList;
import manager.xml.VersionsDocument;
import org.apache.xmlbeans.XmlException;


public class UpgradeService extends UnicastRemoteObject implements IUpgradeService {
    
    public final static String VERSION_RESOURCE = "/version.xml";
    
    static final  Comparator<Version> VER_COMPARATOR = (v1, v2) -> {
        String[] vals1 = v1.getNumber().split("\\.");
        String[] vals2 = v2.getNumber().split("\\.");
        
        int i = 0;
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        } else {
            return Integer.signum(vals1.length - vals2.length);
        }
    };
    
    private final VersionsDocument versionsDocument;

    public UpgradeService() throws Exception {
        super(0, new MultihomeRMIClientSocketFactory(
                InstanceCommunicationService.IFACE_ADDRS.values().stream().map((address) -> {
                    return address.getHostAddress();
                }).collect(Collectors.toList()).toArray(new String[]{})
        ), null);
        if (!getCurrentJar().isFile()) {
            throw new ServiceNotLoadedException(this, "Running application in development mode");
        }
        versionsDocument = VersionsDocument.Factory.parse(this.getClass().getResourceAsStream(VERSION_RESOURCE));
    }
    
    static Version getVersion() {
        try {
            VersionsDocument versionsDocument = VersionsDocument.Factory.parse(UpgradeService.class.getResourceAsStream(VERSION_RESOURCE));
            String currentVersionNumber = versionsDocument.getVersions().getCurrent();
            for (Version version : versionsDocument.getVersions().getVersionArray()) {
                if (version.getNumber().equals(currentVersionNumber)) {
                    return version;
                }
            }
        } catch (IOException | XmlException e) {}
        return null;
    }

    @Override
    public Version getCurrentVersion() throws RemoteException {
        return getVersion();
    }
    
    @Override
    public VersionsDocument getDiffVersions(Version from, Version to) throws RemoteException {
        VersionsDocument resultDocument = VersionsDocument.Factory.newInstance();
        VersionList versionsList = resultDocument.addNewVersions();
        
        List<Version> versions = Arrays.asList(versionsDocument.getVersions().getVersionArray());
        versions.sort(VER_COMPARATOR);
        versions.stream().filter((version) -> {
            return VER_COMPARATOR.compare(version, from) > 0 && VER_COMPARATOR.compare(version, to) <= 0;
        }).forEach((version) -> {
            versionsList.addNewVersion().set(version);
        });
        versionsList.setCurrent(to.getNumber());
        return resultDocument;
    }
    
    @Override
    public RemoteInputStream getUpgradeFileStream() throws RemoteException {
        File jar = getCurrentJar();
        try {
            InputStream in = new FileInputStream(jar);
            
            Logger.getLogger().debug("File stream of ''{0}'' opened for transmission (size: {1})", jar, String.valueOf(in.available()).concat(" bytes"));
            return new RemoteInputStream(new RemoteInputStreamServer(in) {
                boolean started = false;

                @Override
                public byte[] read(int count) throws IOException, RemoteException {
                    byte[] read = super.read(count);
                    if (!started) {
                        started = true;
                        Logger.getLogger().debug("File stream of ''{0}'' transmission started", jar);
                    }
                    if (read.length == 0) {
                        Logger.getLogger().debug("File stream of ''{0}'' transmission finished", jar);
                    }
                    return read;
                }
                
                @Override
                public void close() throws IOException {
                    super.close();
                    Logger.getLogger().debug("File stream of ''{0}'' closed", jar);
                }
            });
        } catch (IOException e) {
            Logger.getLogger().error("Error", e);
        }
        return null;
    }
    
    @Override
    public String getUpgradeFileChecksum() throws RemoteException {
        try {
            byte[] b = Files.readAllBytes(getCurrentJar().toPath());
            byte[] hash = MessageDigest.getInstance("MD5").digest(b);
            return DatatypeConverter.printHexBinary(hash);
        } catch (IOException | NoSuchAlgorithmException e) {}
        return null;
    }
    
    public static File getCurrentJar() {
        try {
            return new File(BuildWC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            Logger.getLogger().error("Unexpected exception", e);
        }
        return null;
    }

}
