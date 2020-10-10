package manager.upgrade;

import codex.context.IContext;
import codex.instance.IInstanceDispatcher;
import codex.instance.IInstanceListener;
import codex.instance.Instance;
import codex.instance.ServiceNotLoadedException;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.model.Access;
import codex.notification.Handler;
import codex.notification.INotificationService;
import codex.notification.Message;
import codex.service.AbstractRemoteService;
import codex.service.ServiceRegistry;
import codex.task.ITaskExecutorService;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.Runtime;
import manager.upgrade.stream.RemoteInputStream;
import manager.upgrade.stream.RemoteInputStreamServer;
import manager.xml.Change;
import manager.xml.Version;
import manager.xml.VersionList;
import manager.xml.VersionsDocument;
import org.apache.xmlbeans.XmlException;
import javax.swing.*;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@LoggingSource
@IContext.Definition(id = "AUS", name = "Application Upgrade Service", icon = "/images/upgrade.png")
public class UpgradeService extends AbstractRemoteService<UpgradeServiceOptions, UpgradeServiceControl> implements IUpgradeService, IContext, IInstanceListener {
    
    private final static String VERSION_RESOURCE = "/version.xml";
    
    public static final  Comparator<Version> VER_COMPARATOR = (v1, v2) -> {
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

    private final Version version;
    private final VersionsDocument history;
    private final Semaphore lock = new Semaphore(1, true);

    public UpgradeService() throws Exception {
        super();

        if (Runtime.APP.devMode.get()) {
            throw new ServiceNotLoadedException(this, "Running application in development mode");
        }
        history = getHistory();
        version = getCurrentVersion();

        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!lock.tryAcquire()) {
                try {
                    lock.acquire();
                } catch (InterruptedException ignore) {
                } finally {
                    lock.release();
                }
            }
        }));
        ServiceRegistry.getInstance().addRegistryListener(IInstanceDispatcher.class, service -> {
            ((IInstanceDispatcher) service).addInstanceListener(this);
        });
    }
    
    static Version getVersion() {
        VersionsDocument versionsDocument = getHistory();
        if (versionsDocument != null) {
            String currentVersionNumber = versionsDocument.getVersions().getCurrent();
            for (Version version : versionsDocument.getVersions().getVersionArray()) {
                if (version.getNumber().equals(currentVersionNumber)) {
                    return version;
                }
            }
        }
        return null;
    }

    static VersionsDocument getHistory() {
        try {
            return VersionsDocument.Factory.parse(UpgradeService.class.getResourceAsStream(VERSION_RESOURCE));
        } catch (IOException | XmlException e) {
            return null;
        }
    }

    @Override
    public Version getCurrentVersion() throws RemoteException {
        return getVersion();
    }
    
    @Override
    public VersionsDocument getDiffVersions(Version from, Version to) throws RemoteException {
        VersionsDocument resultDocument = VersionsDocument.Factory.newInstance();
        VersionList versionsList = resultDocument.addNewVersions();
        
        List<Version> versions = Arrays.asList(history.getVersions().getVersionArray());
        versions.sort(VER_COMPARATOR);
        versions.stream()
                .filter((version) -> VER_COMPARATOR.compare(version, from) > 0 && VER_COMPARATOR.compare(version, to) <= 0)
                .forEach((version) -> versionsList.addNewVersion().set(version));
        versionsList.setCurrent(to.getNumber());
        return resultDocument;
    }
    
    @Override
    public RemoteInputStream getUpgradeFileStream() throws RemoteException {
        File jar = Runtime.APP.jarFile.get();
        try {
            InputStream in = new FileInputStream(jar) {
                {
                    try {
                        lock.acquire();
                    } catch (InterruptedException e) {
                        //
                    }
                }
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        lock.release();
                    }
                }
            };

            Logger.getLogger().debug("File stream of ''{0}'' opened for transmission (size: {1})", jar, String.valueOf(in.available()).concat(" bytes"));
            return new RemoteInputStream(new RemoteInputStreamServer(in) {
                boolean started = false;

                @Override
                public byte[] read(int count) throws IOException {
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
            byte[] b = Files.readAllBytes(Runtime.APP.jarFile.get().toPath());
            byte[] hash = MessageDigest.getInstance("MD5").digest(b);
            return DatatypeConverter.printHexBinary(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            //
        }
        return null;
    }

    @Override
    public void instanceLinked(Instance instance) {
        new Thread(() -> {
            try {
                IUpgradeService remoteUpService = (IUpgradeService) instance.getService(UpgradeService.class);
                Version availVersion = remoteUpService.getCurrentVersion();
                if (availVersion != null && UpgradeService.VER_COMPARATOR.compare(version, availVersion) < 0) {
                    VersionsDocument diff = remoteUpService.getDiffVersions(version, availVersion);

                    ServiceRegistry.getInstance().lookupService(INotificationService.class).sendMessage(
                            Message.getBuilder(UpgradeMessage.class, availVersion::getNumber).build().build(availVersion, diff),
                            Handler.Inbox
                    );
                    Logger.getLogger().info(
                            "Found upgrade provider: {0}\nUpgrade: {1} -> {2}",
                            instance, version.getNumber(), availVersion.getNumber()
                    );
                }
            } catch (RemoteException | NotBoundException ignore) {}
        }).start();
    }

    @Override
    public void instanceUnlinked(Instance instance) {

    }


    static class UpgradeMessage extends Message {

        private static final String PROP_VER  = "upgrade.version";
        private static final String PROP_DIFF = "upgrade.diff";

        public UpgradeMessage(EntityRef owner, String UID) {
            super(owner, UID);

            model.addUserProp(PROP_VER,  new Str(), false, Access.Any);
            model.addUserProp(PROP_DIFF, new Str(), false, Access.Any);
        }

        private String getVersion() {
            return (String) model.getUnsavedValue(PROP_VER);
        }

        private VersionsDocument getDiff() {
            String encoded = (String) model.getUnsavedValue(PROP_DIFF);
            if (encoded != null && !encoded.isEmpty()) {
                InputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(encoded.getBytes()));
                try {
                    return VersionsDocument.Factory.parse(in);
                } catch (XmlException | IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected List<IMessageAction> getActions() {
            Version currVersion = UpgradeService.getVersion();
            Version nextVersion = Version.Factory.newInstance();
            nextVersion.setNumber(getVersion());

            boolean available = currVersion == null || VER_COMPARATOR.compare(nextVersion, currVersion) > 0;
            return  available ?
                    Collections.singletonList(new PerformUpgrade(getVersion(), getDiff())) :
                    super.getActions();
        }

        private UpgradeMessage build(Version availVersion, VersionsDocument diff) {
            Map<Change.Type.Enum, List<Change>> changesByType = Arrays.stream(diff.getVersions().getVersionArray())
                    .map(version -> Arrays.stream(version.getChangelog().getChangeArray()))
                    .flatMap(x -> x)
                    .collect(Collectors.groupingBy(Change::getType));

            setSeverity(changesByType.keySet().contains(Change.Type.BUGFIX) ? Message.Severity.Warning : Message.Severity.Information);
            setSubject(MessageFormat.format(
                    Language.get(UpgradeUnit.class, "msg@upd.title"),
                    availVersion.getNumber()
            ));
            setContent(MessageFormat.format(
                    Language.get(UpgradeUnit.class, "msg@upd.template"),
                    ImageUtils.toBase64(ImageUtils.getByPath("/images/upgrade.png")), 20,
                    availVersion.getDate(),
                    String.join("",
                            !changesByType.containsKey(Change.Type.BUGFIX) ? "" : MessageFormat.format(
                                    Language.get(UpgradeUnit.class, "msg@upd.row.bugfix"),
                                    changesByType.get(Change.Type.BUGFIX).stream()
                                            .map(change -> MessageFormat.format("<li>{0}</li>", change.getDescription()))
                                            .collect(Collectors.joining())
                            ),
                            !changesByType.containsKey(Change.Type.FEATURE) ? "" : MessageFormat.format(
                                    Language.get(UpgradeUnit.class, "msg@upd.row.whatsnew"),
                                    changesByType.get(Change.Type.FEATURE).stream()
                                            .filter(change -> change.getScope() != Change.Scope.API)
                                            .map(change -> MessageFormat.format(
                                                    "<li>{0}</li>",
                                                    change.getDescription()
                                                            .replaceAll("\\n", "<br>")
                                                            .replaceAll("\\*", "&nbsp;&bull;")
                                            ))
                                            .collect(Collectors.joining())
                            )
                    )
            ));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                diff.save(out);
                model.setValue(PROP_DIFF, new String(Base64.getEncoder().encode(out.toByteArray())));
            } catch (IOException ignore) {}
            model.setValue(PROP_VER,  availVersion.getNumber());
            return this;
        }
    }


    static class PerformUpgrade extends Message.AbstractMessageAction {

        private final static ImageIcon ICON  = ImageUtils.getByPath("/images/upgrade.png");
        private final static String    TITLE = Language.get(UpgradeUnit.class, "action@upgrade");

        private final String version;
        private final VersionsDocument diff;

        PerformUpgrade(String version, VersionsDocument diff) {
            super(ICON, TITLE);
            this.version = version;
            this.diff = diff;
        }

        @Override
        public void doAction() {
            ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).quietTask(new LoadUpgrade(version, diff));
        }
    }
}
