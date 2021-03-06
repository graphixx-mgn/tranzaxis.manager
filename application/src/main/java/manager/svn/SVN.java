package manager.svn;

import codex.log.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

public class SVN {

    static {
        SVNDebugLog.setDefaultLog(new SVNDebugLogAdapter() {
            @Override
            public void log(SVNLogType svnLogType, Throwable throwable, Level level) {}
            @Override
            public void log(SVNLogType svnLogType, String s, Level level) {}
            @Override
            public void log(SVNLogType svnLogType, String s, byte[] bytes) {}
        });
    }

    private static final String SVN_PROTOCOL   = "svn";
    private static final String SSH_PROTOCOL   = "svn+";
    private static final String HTTP_PROTOCOL  = "http";
    private static final String HTTPS_PROTOCOL = "https";

    private static int getDefaultPortNumber(String protocol) {
        int port = -1;
        if (SVN_PROTOCOL.equals(protocol)) {
            port = 3690;
        } else if (HTTP_PROTOCOL.equals(protocol)) {
            port = 80;
        } else if (HTTPS_PROTOCOL.equals(protocol)) {
            port = 443;
        } else if (protocol != null && protocol.startsWith(SSH_PROTOCOL)) {
            port = 22;
        }
        return port;
    }

    private static int getPortNumber(SVNURL url) {
        int port = url.getPort();
        return port != 0 ? port : getDefaultPortNumber(url.getProtocol());
    }
    
    public static boolean checkConnection(String path, ISVNAuthenticationManager authMgr) throws SVNException, IOException {
        final SVNURL url  = SVNURL.parseURIEncoded(path);
        final String host = url.getHost();
        final int    port = getPortNumber(url);

        if (port == -1) {
            throw new IOException(MessageFormat.format("Can not get port number from SVN url ''{0}''", path));
        } else {
            try {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 1000);
                    SVNRepository repository = SVNRepositoryFactory.create(url);
                    try {
                        repository.setTunnelProvider(SVNWCUtil.createDefaultOptions(true));
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
    
    public static SVNInfo info(String url, boolean remote, ISVNAuthenticationManager authMgr) {
        return info(url, SVNRevision.HEAD, remote, authMgr);
    }

    public static SVNInfo info(String url, SVNRevision revision, boolean remote, ISVNAuthenticationManager authMgr) {
        SVNInfo info = null;
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);

        try {
            if (remote) {
                SVNURL svnUrl = SVNURL.parseURIEncoded(url);
                info = clientMgr.getWCClient().doInfo(svnUrl, revision, revision);
            } else if (new File(url).exists()) {
                info = clientMgr.getWCClient().doInfo(new File(url), SVNRevision.WORKING);
            }
        } catch (SVNException e) {
            Logger.getLogger().warn("SVN operation ''info'' error: {0}", e.getErrorMessage());
        } finally {
            clientMgr.dispose();
        }
        return info;
    }

    public static List<SVNStatus> status(String path, boolean remote, SVNRevision revision, ISVNAuthenticationManager authMgr) {
        List<SVNStatus> statuses = new LinkedList<>();
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);

        try {
            clientMgr.getStatusClient().doStatus(new File(path), revision, SVNDepth.INFINITY, remote, false, false, false, new ISVNStatusHandler() {
                @Override
                public void handleStatus(SVNStatus svnStatus) throws SVNException {
                    statuses.add(svnStatus);
                }
            }, new LinkedList<>());
        } catch (SVNException e) {
            Logger.getLogger().warn("SVN operation ''status'' error: {0}", e.getErrorMessage());
        } finally {
            clientMgr.dispose();
        }
        return statuses;
    }
    
    public static List<SVNDirEntry> list(String url, ISVNAuthenticationManager authMgr) throws SVNException {
        final List<SVNDirEntry> entries = new LinkedList<>();
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        
        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(url);
            SVNLogClient client = clientMgr.getLogClient();
            client.doList(svnUrl, SVNRevision.HEAD, SVNRevision.HEAD, true, false, entries::add);
        } finally {
            clientMgr.dispose();
        }
        return entries;
    }
    
    public static List<SVNURL> changes(String path, String url, SVNRevision revision, ISVNAuthenticationManager authMgr, ISVNEventHandler handler) throws SVNException {
        List<SVNURL> changes = new ArrayList<>();
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        final File localDir = new File(path);

        Predicate<SVNStatus> updateable = status ->
                status.getNodeStatus() != SVNStatusType.STATUS_UNVERSIONED &&
                status.getCombinedRemoteNodeAndContentsStatus() != SVNStatusType.STATUS_NONE && (
                        status.getKind() == SVNNodeKind.FILE ||
                        status.getRemoteKind() == SVNNodeKind.FILE
                );
        try {
            if (!SVNWCUtil.isVersionedDirectory(localDir)) {
                // Checkout
                SVNURL svnUrl = SVNURL.parseURIEncoded(url);
                SVNLogClient logClient = clientMgr.getLogClient();
                logClient.doList(svnUrl, revision, revision, true, true, entry -> {
                    if (handler != null) {
                        handler.checkCancelled();
                    }
                    if (entry.getKind() == SVNNodeKind.FILE) {
                        changes.add(entry.getURL());
                    }
                });
            } else {
                SVNStatusClient statusClient = clientMgr.getStatusClient();
                if (handler != null) {
                    statusClient.setEventHandler(handler);
                }

                SVNStatus wcstatus = statusClient.doStatus(localDir, false);
                if (wcstatus.isLocked()) {
                    SVNWCClient client = clientMgr.getWCClient();
                    if (handler != null) handler.handleEvent(
                        new SVNEvent(SVNErrorMessage.create(SVNErrorCode.WC_CLEANUP_REQUIRED), SVNEventAction.RESOLVER_STARTING), 0
                    );
                    client.doCleanup(localDir, true, true, true, false, false, false);
                    if (handler != null) handler.handleEvent(
                        new SVNEvent(SVNErrorMessage.create(SVNErrorCode.WC_CLEANUP_REQUIRED), SVNEventAction.RESOLVER_DONE), 0
                    );
                }
                try {
                    statusClient.doStatus(localDir, revision, SVNDepth.INFINITY, true, false, false, false, status -> {
                        if (handler != null) {
                            handler.checkCancelled();
                        }
                        if (updateable.test(status)) {
                            changes.add(status.getURL());
                        }
                    }, null);
                } catch (SVNCancelException e) {
                    // Do nothing
                }
            }
        } finally {
            clientMgr.dispose();
        }
        return changes;
    }
    
    public static void update(String url, String path, SVNRevision revision, ISVNAuthenticationManager authMgr, ISVNEventHandler handler) throws SVNException {
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        final SVNUpdateClient updateClient = clientMgr.getUpdateClient();
        updateClient.setIgnoreExternals(false);
        if (handler != null) {
            updateClient.setEventHandler(handler);
        }
        final File localDir = new File(path);
        try {
            if (!SVNWCUtil.isVersionedDirectory(localDir)) {
                SVNURL svnUrl = SVNURL.parseURIEncoded(url);
                updateClient.doCheckout(svnUrl, localDir, SVNRevision.UNDEFINED, revision, SVNDepth.INFINITY, false);
            } else {
                updateClient.doUpdate(localDir, revision, SVNDepth.INFINITY, false, false);
            }
        } finally {
            clientMgr.dispose();
        }
    }    
    
    public static void export(String url, String path, ISVNAuthenticationManager authMgr) throws SVNException {
        export(url, path, authMgr, null);
    }
    
    public static void export(String url, String path, ISVNAuthenticationManager authMgr, SVNDepth depth) throws SVNException {
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(url);
            SVNUpdateClient client = clientMgr.getUpdateClient();
            client.doExport(svnUrl, new File(path), SVNRevision.HEAD, SVNRevision.HEAD, null, true, depth != null ? depth : SVNDepth.INFINITY);
        } finally {
            clientMgr.dispose();
        }
    }
    
    public static InputStream readFile(String url, String path, ISVNAuthenticationManager authMgr) throws SVNException {
        SVNRepositoryFactoryImpl.setup();
        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
        repository.setAuthenticationManager(authMgr);
        repository.setTunnelProvider(SVNWCUtil.createDefaultOptions(true));

        SVNProperties properties = new SVNProperties();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        repository.getFile(path, -1, properties, baos);

        return new ByteArrayInputStream(baos.toByteArray());
    }

    public static SVNRevision getMinimalRevision(String url, ISVNAuthenticationManager authMgr) throws SVNException {
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        AtomicReference<SVNLogEntry> logEntry = new AtomicReference<>();
        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(url);
            SVNLogClient client = clientMgr.getLogClient();
            client.doLog(svnUrl, new String[]{""}, SVNRevision.HEAD, SVNRevision.create(1), SVNRevision.HEAD, true, false, 1, logEntry::set);
        } finally {
            clientMgr.dispose();
        }

        return logEntry.get() != null ? SVNRevision.create(logEntry.get().getRevision()) : SVNRevision.UNDEFINED;
    }

    public static List<SVNLogEntry> log(String url, SVNRevision from, SVNRevision to, long limit, ISVNAuthenticationManager authMgr) throws SVNException {
        List<SVNLogEntry> log = new LinkedList<>();
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);

        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(url);
            SVNLogClient client = clientMgr.getLogClient();
            client.doLog(svnUrl, new String[]{""}, SVNRevision.HEAD, from, to, true, false, limit, log::add);
        } finally {
            clientMgr.dispose();
        }
        return log;
    }

    public static void resolve(File file, ISVNAuthenticationManager authMgr) throws SVNException {
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);

        try {
            SVNWCClient client = clientMgr.getWCClient();
            client.doResolve(file, SVNDepth.IMMEDIATES, SVNConflictChoice.THEIRS_FULL);
        } finally {
            clientMgr.dispose();
        }
    }
    
}
