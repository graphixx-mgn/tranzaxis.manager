package manager.svn;

import codex.log.Logger;
import java.util.LinkedList;
import java.util.List;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;


public class SVN {
    
    public static List<SVNDirEntry> list(String url, String user, String pass) {
        List<SVNDirEntry> entries = new LinkedList<>();
        
        SVNAuthentication auth = new SVNPasswordAuthentication(user, pass, false);
        ISVNAuthenticationManager authMgr = new BasicAuthenticationManager(new SVNAuthentication[] { auth });
        SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
        
        try {
            SVNURL svnUrl = SVNURL.parseURIEncoded(url);
            SVNLogClient client = clientMgr.getLogClient();
            client.doList(svnUrl, SVNRevision.HEAD, SVNRevision.HEAD, true, false, (entry) -> {
                entries.add(entry);
            });
        } catch (SVNException e) {
             Logger.getLogger().warn("SVN operation ''list'' error: {0}", e.getErrorMessage());
        } finally {
            clientMgr.dispose();
        }
        return entries;
    }
    
}
