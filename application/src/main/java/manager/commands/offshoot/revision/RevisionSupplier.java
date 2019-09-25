package manager.commands.offshoot.revision;

import codex.supplier.IDataSupplier;
import manager.nodes.Offshoot;
import manager.svn.SVN;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.util.*;

public class RevisionSupplier implements IDataSupplier<Map<String, String>> {

    private final Offshoot    offshoot;
    private final SVNRevision headRevision;

    private SVNRevision prevEdgeRevision, nextEdgeRevision;
    private Boolean     nextAvailable = true;
    private Boolean     prevAvailable = true;

    public RevisionSupplier(Offshoot offshoot) {
        this.offshoot = offshoot;
        headRevision  = offshoot.getWorkingCopyRevision(true);
    }

    @Override
    public boolean ready() {
        return offshoot.getRepository().isRepositoryOnline(false);
    }

    @Override
    public boolean available(ReadDirection direction) {
        return
                direction.equals(ReadDirection.Forward) && nextAvailable ||
                direction.equals(ReadDirection.Backward) && prevAvailable;
    }

    @Override
    public List<Map<String, String>> getNext() throws LoadDataException {
        List<Map<String, String>> result = new LinkedList<>();
        try {
            List<SVNLogEntry> log = SVN.log(
                    offshoot.getRemotePath(),
                    nextEdgeRevision,
                    SVNRevision.create(1),
                    IDataSupplier.DEFAULT_LIMIT,
                    offshoot.getRepository().getAuthManager()
            );
            log.forEach(logEntry -> result.add(
                    new LinkedHashMap<String, String>(){{
                        put("Revision", String.valueOf(logEntry.getRevision()));
                        put("Date",     Offshoot.DATE_FORMAT.format(logEntry.getDate()));
                        put("Author",   logEntry.getAuthor());
                        put("Message",  logEntry.getMessage());
                    }}
            ));
            if (result.size() < IDataSupplier.DEFAULT_LIMIT) {
                nextAvailable = false;
            } else {
                nextEdgeRevision = SVNRevision.create(log.get(log.size()-1).getRevision()-1);
            }
        } catch (SVNException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public List<Map<String, String>> getPrev() throws LoadDataException {
        List<Map<String, String>> result = new LinkedList<>();
        try {
            List<SVNLogEntry> log = SVN.log(
                    offshoot.getRemotePath(),
                    prevEdgeRevision,
                    headRevision,
                    IDataSupplier.DEFAULT_LIMIT,
                    offshoot.getRepository().getAuthManager()
            );
            log.forEach(logEntry -> result.add(
                    new LinkedHashMap<String, String>(){{
                        put("Revision", String.valueOf(logEntry.getRevision()));
                        put("Date",     Offshoot.DATE_FORMAT.format(logEntry.getDate()));
                        put("Author",   logEntry.getAuthor());
                        put("Message",  logEntry.getMessage());
                    }}
            ));
            if (result.size() < IDataSupplier.DEFAULT_LIMIT) {
                prevAvailable = false;
            } else {
                prevEdgeRevision = SVNRevision.create(log.get(log.size()-1).getRevision()+1);
            }
        } catch (SVNException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public void reset() {
        if (SVNWCUtil.isVersionedDirectory(new File(offshoot.getLocalPath())) && offshoot.getWCStatus() != WCStatus.Absent) {
            SVNRevision currentRevision = offshoot.getWorkingCopyRevision(false);
            nextEdgeRevision = SVNRevision.create(Math.min(currentRevision.getNumber() + IDataSupplier.DEFAULT_LIMIT / 2, headRevision.getNumber()));
        } else {
            nextEdgeRevision = headRevision;
        }
        prevEdgeRevision = SVNRevision.create(nextEdgeRevision.getNumber()+1);

        nextAvailable = true;
        prevAvailable = prevEdgeRevision.getNumber() < headRevision.getNumber();
    }
}
