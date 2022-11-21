package manager.nodes;

import codex.command.EditorCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.mask.RegexMask;
import codex.model.*;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.repository.LoadWC;
import manager.type.SVNAuth;
import org.apache.commons.io.FilenameUtils;
import org.atteo.classindex.ClassIndex;
import org.codehaus.plexus.util.ExceptionUtils;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHConnector;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Repository extends Entity {

    static {
        System.setProperty("svnkit.ssh.client", "apache");
    }

    private final static ImageIcon ICON_ONLINE  = ImageUtils.getByPath("/images/repository.png");
    private final static ImageIcon ICON_OFFLINE = ImageUtils.combine(
            ICON_ONLINE,
            ImageUtils.resize(ImageUtils.getByPath("/images/stop.png"), .6f),
            SwingConstants.SOUTH_EAST
    );
    private final static ImageIcon ICON_AUTH  = ImageUtils.getByPath("/images/auth.png");

           final static int ERR_AUTH_FAILED   = 170000;
           final static int ERR_NOT_AVAILABLE = 210000;
    
    public final static String PROP_REPO_URL  = "repoUrl";
    public final static String PROP_ARCHIVE   = "loadArchive";
    public final static String PROP_AUTH_MODE = "authMode";
    public final static String PROP_SVN_USER  = "svnUser";
    public final static String PROP_SVN_PASS  = "svnPass";
    public final static String PROP_SVN_FILE  = "svnFile";
    public final static String PROP_LOADED    = "loaded";
    public final static String PROP_ONLINE    = "online";

    private final static Map<String, Class<? extends RepositoryBranch>> BRANCHES = new HashMap<>();
    private final static IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);

    static {
        ClassIndex.getSubclasses(RepositoryBranch.class).forEach(branchClass -> BRANCHES.put(
                branchClass.getAnnotation(RepositoryBranch.Branch.class).remoteDir(),
                branchClass
        ));
        CommandRegistry.getInstance().registerCommand(LoadWC.class);
    }

    public static String formatErrorMessage(String whatsWrong, Exception exception) {
        if (exception instanceof SVNException) {
            SVNErrorCode code = ((SVNException) exception).getErrorMessage().getErrorCode();
            switch (code.getCategory()) {
                case ERR_AUTH_FAILED:
                    return MessageFormat.format(Language.get(Repository.class, "error@unavailable.auth"), whatsWrong);
                case ERR_NOT_AVAILABLE:
                    return MessageFormat.format(Language.get(Repository.class, "error@unavailable.repo"), whatsWrong);
                default:
                    return MessageFormat.format(Language.get(Repository.class, "error@unavailable.other"), whatsWrong, code.getDescription());
            }
        }
        return MessageFormat.format(Language.get(Repository.class, "error@unavailable.other"), whatsWrong, exception.getMessage());
    }

    public Repository(EntityRef<Common> owner, String title) {
        super(owner, ICON_ONLINE, title, null);

        PropertyHolder<Str, String>    svnUser = new PropertyHolder<>(PROP_SVN_USER, new Str(null), true);
        PropertyHolder<Str, String>    svnPass = new PropertyHolder<>(PROP_SVN_PASS, new Str(null), true);
        PropertyHolder<FilePath, Path> svnFile = new PropertyHolder<>(PROP_SVN_FILE, new FilePath(null), true);
        
        // Properties
        model.addUserProp(PROP_REPO_URL,  new Str(null).setMask(new RegexMask("svn(|\\+[\\w]+)://[\\w\\./\\d]+", "Invalid SVN url")), true, null);
        model.addUserProp(PROP_ARCHIVE,   new Bool(false), true, Access.Select);
        model.addUserProp(PROP_AUTH_MODE, new Enum<>(SVNAuth.None), false, Access.Any);
        model.addUserProp(svnUser, Access.Any);
        model.addUserProp(svnPass, Access.Any);
        model.addUserProp(svnFile, Access.Any);
        model.addUserProp(PROP_LOADED,    new Bool(false), false, Access.Any);
        model.addDynamicProp(PROP_ONLINE, new Bool(null), Access.Any, () -> {
            try {
                return checkConnection();
            } catch (SVNException | IOException e) {
                return false;
            }
        }, PROP_LOADED);
        
        // Property settings
        model.setPropUnique(PROP_REPO_URL);
        
        // Editor settings
        AuthSettings authSettings = new AuthSettings();
        model.getEditor(PROP_REPO_URL).addCommand(authSettings);

        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case PROP_AUTH_MODE:
                    model.getEditor(PROP_SVN_USER).setVisible(getAuthMode(true) == SVNAuth.Password || getAuthMode(true) == SVNAuth.Certificate);
                    model.getEditor(PROP_SVN_PASS).setVisible(getAuthMode(true) == SVNAuth.Password);
                    model.getEditor(PROP_SVN_FILE).setVisible(getAuthMode(true) == SVNAuth.Certificate);
                    svnUser.setRequired(getAuthMode(true) == SVNAuth.Password || getAuthMode(true) == SVNAuth.Certificate);
                    svnPass.setRequired(getAuthMode(true) == SVNAuth.Password);
                    svnFile.setRequired(getAuthMode(true) == SVNAuth.Certificate);
                    break;
                case PROP_LOADED:
                    lockEditors(Boolean.FALSE.equals(newValue));
                    model.updateDynamicProps(PROP_ONLINE);
                    if (Boolean.TRUE.equals(isLoaded(true))) {
                        setIcon(isRepositoryOnline(false) ? ICON_ONLINE : ICON_OFFLINE);
                    } else {
                        setIcon(ImageUtils.getByPath("/images/repository.png"));
                    }
                    break;
            }
        });
        refreshProperties();
        setMode((isLoaded(false) && getChildCount() > 0 ? INode.MODE_ENABLED : INode.MODE_NONE) + INode.MODE_SELECTABLE);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent != null && isLoaded(true)) {
            getCommand(LoadWC.class).load(this);
        }
    }

    @Deprecated
    public final Boolean isLoadArchive() {
        return Boolean.TRUE.equals(model.getValue(PROP_ARCHIVE));
    }

    public final SVNAuth getAuthMode(boolean unsaved) {
        return (SVNAuth) (unsaved ? model.getUnsavedValue(PROP_AUTH_MODE) : model.getValue(PROP_AUTH_MODE));
    }
    
    public final String getSvnUser(boolean unsaved) {
        return (String) (unsaved ? model.getUnsavedValue(PROP_SVN_USER) : model.getValue(PROP_SVN_USER));
    }

    public final Path getKeyFile() {
        return (Path) model.getValue(PROP_SVN_FILE);
    }
    
    public final String getSvnPass(boolean unsaved) {
        return (String) (unsaved ? model.getUnsavedValue(PROP_SVN_PASS) : model.getValue(PROP_SVN_PASS));
    }
    
    public final boolean isLoaded(boolean unsaved) {
        return (unsaved ? model.getUnsavedValue(PROP_LOADED) : model.getValue(PROP_LOADED)) == Boolean.TRUE;
    }

    public boolean isRepositoryOnline(boolean showDialog) {
        if (showDialog) {
            try {
                return checkConnection();
            } catch (SVNException | IOException e) {
                MessageBox.show(
                        MessageType.WARNING,
                        formatErrorMessage(MessageFormat.format(Language.get(Repository.class, "fail@connect"), getPID()), e)
                );
                return false;
            }
        } else {
            return Boolean.TRUE.equals(model.getValue(PROP_ONLINE));
        }
    }
    
    public final void setLocked(boolean value) {
        model.setValue(PROP_LOADED, value);
    }

    private List<RepositoryBranch> getLocalBranches() {
        return BRANCHES.values().stream()
                .map(branchClass -> (RepositoryBranch) Entity.newInstance(branchClass, this.toRef(), null))
                .filter(branchClass -> !CAS.readCatalogEntries(this.getID(), branchClass.getChildClass()).isEmpty())
                .collect(Collectors.toList());
    }

    private List<RepositoryBranch> getBranches() {
        List<RepositoryBranch> branches = new LinkedList<>();
        if (isRepositoryOnline(false)) {
            try {
                listEntries(getUrl(), false, null).forEach(svnDirEntry -> {
                    if (BRANCHES.containsKey(svnDirEntry.getName())) {
                        branches.add(Entity.newInstance(
                                BRANCHES.get(svnDirEntry.getName()),
                                this.toRef(),
                                null
                        ));
                    }
                });
            } catch (SVNException e) {
                branches.addAll(getLocalBranches());
            }
        } else {
            branches.addAll(getLocalBranches());
        }
        return branches;
    }

    public void loadBranches() {
        getBranches().forEach(RepositoryBranch::loadBranch);
    }

    public void unloadBranches() {
        getBranches().forEach(RepositoryBranch::unloadBranch);
    }

    @Deprecated
    public static String urlToDirName(String url) {
        return url.replaceAll("([\\w+]+)://([^/:]*)([:\\d]*)(.*)", "$2$4").replaceAll("[/\\\\]", ".");
    }

    private void lockEditors(boolean editable) {
        model.getEditor(PROP_REPO_URL).setEditable(editable);
        model.getEditor(PROP_ARCHIVE).setEditable(editable);
        model.getEditor(PROP_AUTH_MODE).setEditable(editable);
        model.getEditor(PROP_SVN_USER).setEditable(editable);
        model.getEditor(PROP_SVN_PASS).setEditable(editable);
    }

    @Deprecated
    public final String getRepoUrl() {
        return (String) model.getValue(PROP_REPO_URL);
    }

    public final SVNURL getUrl() {
        try {
            return SVNURL.parseURIEncoded((String) model.getValue(PROP_REPO_URL));
        } catch (SVNException e) {
            throw new Error(e);
        }
    }

    public final SVNURL getDirUrl(String dir) throws SVNException {
        return getUrl().appendPath(FilenameUtils.separatorsToUnix(dir), true);
    }

    public static String getErrorMessage(Throwable error) {
        return Arrays.stream(ExceptionUtils.getThrowables(error))
                .skip(1)
                .map(throwable -> MessageFormat.format(" * {0}", throwable.getMessage()))
                .distinct()
                .collect(Collectors.joining("\n"));
    }

    public final ISVNOptions getOptions() throws SVNException {
        return new DefaultSVNOptions(null, true) {{
            if (Protocol.getProtocol(getUrl()) == Protocol.SSH && getAuthMode(true) != SVNAuth.None) {
                setTunnelProvider((url -> new SVNSSHConnector(false, false)));
            }
        }};
    }

    public final ISVNAuthenticationManager getAuthManager() {
        SVNAuth authType = getAuthMode(true);
        switch (authType) {
            case Password:
                return new BasicAuthenticationManager(new SVNAuthentication[] {
                        SVNPasswordAuthentication.newInstance(getSvnUser(true), getSvnPass(true).toCharArray(), false, null, true)
                });
            case Certificate:
                return new BasicAuthenticationManager(new SVNAuthentication[] {
                        SVNSSHAuthentication.newInstance(
                                getSvnUser(true),
                                getKeyFile().toFile(),
                                "".toCharArray(),
                                22, false, null, false
                        )
                });
            default:
                return new BasicAuthenticationManager(new SVNAuthentication[]{});
        }
    }

    public final boolean checkConnection() throws SVNException, IOException {
        final SVNURL url  = getUrl();
        final String host = url.getHost();
        final int    port = Protocol.getPort(url);
        if (port == -1) {
            throw new IOException(MessageFormat.format("Can not get port number from SVN url ''{0}''", url));
        } else {
            try {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 1000);
                    SVNRepository repository = SVNRepositoryFactory.create(url);
                    try {
                        repository.setAuthenticationManager(getAuthManager());
                        repository.setTunnelProvider(getOptions());
                        repository.testConnection();
                        return true;
                    } finally {
                        repository.closeSession();
                    }
                }
            } catch (IOException e) {
                throw new IOException(MessageFormat.format("Can not connect to remote host ''{0}:{1}''", host, port));
            }
        }
    }

    public final List<SVNDirEntry> listEntries(SVNURL url, boolean recursive, ISVNEventHandler handler) throws SVNException {
        final List<SVNDirEntry> entries = new LinkedList<>();
        final SVNClientManager  manager = SVNClientManager.newInstance(getOptions(), getAuthManager());
        try {
            manager.getLogClient().doList(url, SVNRevision.HEAD, SVNRevision.HEAD, true, recursive, svnDirEntry -> {
                if (handler != null) {
                    handler.checkCancelled();
                }
                entries.add(svnDirEntry);
            });
        } finally {
            manager.dispose();
        }
        return entries;
    }

    public final SVNInfo getInfo(SVNURL url, SVNRevision revision) throws SVNException {
        final SVNClientManager manager = SVNClientManager.newInstance(getOptions(), getAuthManager());
        try {
            return manager.getWCClient().doInfo(
                    url,
                    revision == null ? SVNRevision.HEAD : revision,
                    revision == null ? SVNRevision.HEAD : revision
            );
        } catch (SVNException e) {
            Logger.getLogger().warn("SVN operation ''info'' error: {0}", e.getErrorMessage());
            throw e;
        } finally {
            manager.dispose();
        }
    }

    public final SVNInfo getInfo(File file) throws SVNException {
        final SVNClientManager manager = SVNClientManager.newInstance(getOptions(), getAuthManager());
        try {
            return manager.getWCClient().doInfo(file, SVNRevision.WORKING);
        } catch (SVNException e) {
            Logger.getLogger().warn("SVN operation ''info'' error: {0}", e.getErrorMessage());
            throw e;
        } finally {
            manager.dispose();
        }
    }

    public List<SVNStatus> getStatus(Path path, boolean remote, SVNRevision revision) throws SVNException {
        final List<SVNStatus> statuses = new LinkedList<>();
        final SVNClientManager manager = SVNClientManager.newInstance(getOptions(), getAuthManager());
        try {
            manager.getStatusClient().doStatus(path.toFile(), revision, SVNDepth.INFINITY, remote, false, false, false, statuses::add, new LinkedList<>());
        } catch (SVNException e) {
            Logger.getLogger().warn("SVN operation ''status'' error: {0}", e.getErrorMessage());
            throw e;
        } finally {
            manager.dispose();
        }
        return statuses;
    }

    public List<SVNDirEntry> getChanges(Path path, SVNRevision revision, ISVNEventHandler handler) throws SVNException {
        final SVNClientManager manager = SVNClientManager.newInstance(getOptions(), getAuthManager());
        List<SVNDirEntry> changes = new ArrayList<>();
        try {
            if (manager.getStatusClient().doStatus(path.toFile(), false).isLocked()) {
                SVNWCClient client = manager.getWCClient();
                if (handler != null) handler.handleEvent(
                    new SVNEvent(SVNErrorMessage.create(SVNErrorCode.WC_CLEANUP_REQUIRED), SVNEventAction.RESOLVER_STARTING), 0
                );
                client.doCleanup(path.toFile(), true, true, true, false, false, false);
                if (handler != null) handler.handleEvent(
                    new SVNEvent(SVNErrorMessage.create(SVNErrorCode.WC_CLEANUP_REQUIRED), SVNEventAction.RESOLVER_DONE), 0
                );
            }
            final Predicate<SVNStatus> update = status ->
                    status.getNodeStatus() != SVNStatusType.STATUS_UNVERSIONED &&
                    status.getCombinedRemoteNodeAndContentsStatus() != SVNStatusType.STATUS_NONE && (
                        status.getKind() == SVNNodeKind.FILE ||
                        status.getRemoteKind() == SVNNodeKind.FILE
                    );
            manager.getStatusClient().doStatus(path.toFile(), revision, SVNDepth.INFINITY, true, false, false, false, status -> {
                if (handler != null) {
                    handler.checkCancelled();
                }
                if (update.test(status)) {
                    changes.add(new SVNDirEntry(
                            status.getRemoteURL(),
                            status.getRepositoryRootURL(),
                            status.getFile().getName(),
                            status.getRemoteKind(),
                            status.getFile().length(),
                            false,
                            status.getRevision().getNumber(),
                            status.getRemoteDate(),
                            status.getAuthor()
                    ));
                }
            }, null);
        } finally {
            manager.dispose();
        }
        return changes;
    }

    public void update(SVNURL url, Path path, SVNRevision revision, ISVNEventHandler handler) throws SVNException {
        final SVNClientManager manager = SVNClientManager.newInstance(getOptions(), getAuthManager());
        try {
            final SVNUpdateClient updateClient = manager.getUpdateClient();
            updateClient.setIgnoreExternals(false);
            if (handler != null) {
                updateClient.setEventHandler(handler);
            }
            if (!SVNWCUtil.isVersionedDirectory(path.toFile())) {
                updateClient.doCheckout(url, path.toFile(), SVNRevision.UNDEFINED, revision, SVNDepth.INFINITY, false);
            } else {
                updateClient.doUpdate(path.toFile(), revision, SVNDepth.INFINITY, false, false);
            }
        } finally {
            manager.dispose();
        }
    }

    public void resolve(File file) throws SVNException {
        final SVNClientManager manager = SVNClientManager.newInstance(getOptions(), getAuthManager());
        try {
            manager.getWCClient().doResolve(file, SVNDepth.IMMEDIATES, SVNConflictChoice.THEIRS_FULL);
        } finally {
            manager.dispose();
        }
    }


    class AuthSettings extends EditorCommand<Str, String> {

        private final List<String> props = Arrays.asList(PROP_AUTH_MODE, PROP_SVN_USER, PROP_SVN_PASS, PROP_SVN_FILE);

        AuthSettings() {
            super(ICON_AUTH, Language.get(Repository.class, "group@auth"));
        }

        @Override
        public void execute(PropertyHolder<Str, String> context) {
            new PropSetEditor(
                    ICON_AUTH,
                    Language.get(Repository.class, "group@auth"),
                    Repository.this,
                    props::contains
            ).open();
        }
    }

    enum Protocol {
        SVN("^svn$",      3690),
        SSH("^svn\\+.*$", 22),
        HTTP("^http$",    80),
        HTTPS("^https$",  443);

        private final String  regex;
        private final Integer port;

        Protocol(String  regex, Integer port) {
            this.regex = regex;
            this.port  = port;
        }

        private static int calcPort(SVNURL url) {
            return getProtocol(url).port;
        }

        static Protocol getProtocol(SVNURL url) {
            for (Protocol protocol : Protocol.values()) {
                if (url.getProtocol().matches(protocol.regex)) {
                    return protocol;
                }
            }
            return SVN;
        }

        static int getPort(SVNURL url) {
            int port = url.getPort();
            return port != 0 ? port : calcPort(url);
        }
    }
}