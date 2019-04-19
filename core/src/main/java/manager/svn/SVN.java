package manager.svn;

import codex.log.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;

import codex.type.Str;
import codex.utils.NetTools;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnDiffSummarize;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;


public class SVN {

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
                    socket.connect(new InetSocketAddress(host, port), 500);
                    SVNRepository repository = SVNRepositoryFactory.create(url);
                    try {
                        repository.setTunnelProvider(SVNWCUtil.createDefaultOptions(true));
                        repository.testConnection();
                        return true;
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_AUTHORIZED) {
                            return false;
                        } else {
                            throw e;
                        }
                    } finally {
                        repository.closeSession();
                    }
                }
            } catch (IOException e) {
                throw new IOException(MessageFormat.format("Can not connect to remote host ''{0}:{1}''", host, port));
            }
        }
    }
    
    public static SVNInfo info(String url, boolean remote, ISVNAuthenticationManager authMgr){
        SVNInfo info = null;
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        
        try {
            if (remote) {
                SVNURL svnUrl = SVNURL.parseURIEncoded(url);
                info = clientMgr.getWCClient().doInfo(svnUrl, SVNRevision.HEAD, SVNRevision.HEAD);
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

    public static SVNStatus status(String url, boolean remote, ISVNAuthenticationManager authMgr){
        SVNStatus status = null;
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);

        try {
            status = clientMgr.getStatusClient().doStatus(new File(url), remote);
        } catch (SVNException e) {
            Logger.getLogger().warn("SVN operation ''status'' error: {0}", e.getErrorMessage());
        } finally {
            clientMgr.dispose();
        }
        return status;
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
    
    public static List<Path> changes(String path, String url, SVNRevision revision, ISVNAuthenticationManager authMgr, ISVNEventHandler handler) throws SVNException {
        List<Path> changes = new ArrayList<>();
        final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        final File localDir = new File(path);

        try {
            if (!SVNWCUtil.isVersionedDirectory(localDir)) {
                // Checkout
                SVNURL svnUrl = SVNURL.parseURIEncoded(url);
                SVNLogClient logClient = clientMgr.getLogClient();
                logClient.doList(svnUrl, revision, revision, true, true, entry -> {
                    if (handler != null) {
                        handler.checkCancelled();
                    }
                    File file = new File(localDir, entry.getURL().getPath().replace(svnUrl.getPath(), ""));
                    if (entry.getKind() == SVNNodeKind.FILE) {
                        changes.add(file.toPath());
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
                    SVNURL svnUrl = SVNURL.parseURIEncoded(url);
                    statusClient.doStatus(localDir, SVNRevision.WORKING, SVNDepth.INFINITY, true, false, false, false, new ISVNStatusHandler() {
                        @Override
                        public void handleStatus(SVNStatus status) throws SVNException {
                            if (handler != null) {
                                handler.checkCancelled();
                            }
                            if (status.getCombinedNodeAndContentsStatus() != SVNStatusType.STATUS_NORMAL || status.getCombinedRemoteNodeAndContentsStatus() != SVNStatusType.STATUS_NONE) {
                                if (status.getNodeStatus() != SVNStatusType.STATUS_UNVERSIONED && (status.getFile().isFile() || status.getKind() == SVNNodeKind.FILE || status.getRemoteKind() == SVNNodeKind.FILE)) {
                                    File file = status.getFile() != null ? status.getFile() : new File(localDir, status.getURL().getPath().replace(svnUrl.getPath(), ""));
                                    changes.add(file.toPath());
                                }
                            }
                        }
                    }, null);

                    if (wcstatus.isVersioned()) {
                        SVNRevision current = statusClient.doStatus(localDir, false).getRevision();
                        final SvnDiffSummarize diff = new SvnOperationFactory().createDiffSummarize();
                        diff.setSources(
                                SvnTarget.fromFile(localDir, current),
                                SvnTarget.fromURL(SVNURL.parseURIEncoded(url), revision)
                        );
                        diff.setRecurseIntoDeletedDirectories(true);
                        diff.setReceiver((target, status) -> {
                            if (handler != null) {
                                handler.checkCancelled();
                            }

                            File file = new File(localDir, status.getUrl().getPath().replace(svnUrl.getPath(), ""));
                            if (!changes.contains(file.toPath()) && status.getKind() == SVNNodeKind.FILE) {
                                changes.add(file.toPath());
                            }
                        });
                        diff.run();
                    }
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
    
}
