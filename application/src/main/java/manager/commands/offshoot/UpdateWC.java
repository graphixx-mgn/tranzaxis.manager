package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.task.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.nio.channels.ClosedChannelException;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import manager.nodes.BinarySource;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import org.codehaus.plexus.util.StringUtils;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;
import javax.swing.*;


@EntityCommand.Definition(parentCommand = RefreshWC.class)
public class UpdateWC extends EntityCommand<Offshoot> {

    private static final ImageIcon COMMAND_ICON = ImageUtils.combine(
            ImageUtils.getByPath("/images/folder.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/up.png"), .7f),
            SwingConstants.SOUTH_EAST
    );

    public UpdateWC() {
        super(
                "update",
                Language.get("title"),
                COMMAND_ICON,
                Language.get("desc"), 
                (offshoot) -> offshoot.getRepository().isRepositoryOnline(false)
        );
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public ITask getTask(Offshoot context, Map<String, IComplexType> params) {
        return new UpdateWC.UpdateTask(context, SVNRevision.HEAD);
    }

    @Override
    public void execute(Offshoot context, Map<String, IComplexType> map) {
        executeTask(context, getTask(context, map));
    }
    
    public static class UpdateTask extends AbstractTask<Void> {

        private final Offshoot    offshoot;
        private final SVNRevision revision;

        UpdateTask(Offshoot offshoot, SVNRevision revision) {
            super(MessageFormat.format(
                    Language.get(UpdateWC.class, "task@title"),
                    offshoot.getRepository().getPID(),
                    offshoot.getPID(),
                    revision
            ));
            this.offshoot = offshoot;
            this.revision = revision;
        }
        
        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            if (offshoot.getWCStatus().isOperative()) {
                WCStatus checked = offshoot.checkConflicts();
                if (checked == WCStatus.Erroneous) {
                    throw new ExecuteException(
                            Language.get(Offshoot.class, "conflicts@error"),
                            Language.get(Offshoot.class, "conflicts@error", Language.DEF_LOCALE)
                    );
                }
            }
            setProgress(0, Language.get(UpdateWC.class, "command@calc"));
            try {
                List<SVNDirEntry> changes = !SVNWCUtil.isVersionedDirectory(offshoot.getLocalPath().toFile()) ?
                        offshoot.getRepository().listEntries(offshoot.getRemotePath(), true, new ISVNEventHandler() {
                            @Override
                            public void handleEvent(SVNEvent svnEvent, double v) throws SVNException {}
                            @Override
                            public void checkCancelled() throws SVNCancelException {
                                checkPaused();
                                if (UpdateTask.this.isCancelled()) {
                                    throw new SVNCancelException();
                                }
                            }
                        }).parallelStream().filter(svnDirEntry -> svnDirEntry.getKind() == SVNNodeKind.FILE).collect(Collectors.toList())
                        :
                        offshoot.getRepository().getChanges(offshoot.getLocalPath(), revision, new ISVNEventHandler() {
                            @Override
                            public void handleEvent(SVNEvent event, double d) {
                                if (event.getErrorMessage() != null && event.getErrorMessage().getErrorCode() == SVNErrorCode.WC_CLEANUP_REQUIRED) {
                                    if (event.getExpectedAction() == SVNEventAction.RESOLVER_STARTING) {
                                        setProgress(0, Language.get(UpdateWC.class, "command@cleanup"));
                                        Logger.getLogger().info(
                                                "Perform automatic cleanup repository [{0}/{1}]",
                                                offshoot.getRepository().getPID(),
                                                offshoot.getPID()
                                        );
                                    } else {
                                        Logger.getLogger().info(
                                                "Continue update [{0}/{1}] after cleanup",
                                                offshoot.getRepository().getPID(),
                                                offshoot.getPID()
                                        );
                                        setProgress(0, Language.get(UpdateWC.class, "command@calc"));
                                    }
                                }
                            }

                            @Override
                            public void checkCancelled() throws SVNCancelException {
                                checkPaused();
                                if (UpdateTask.this.isCancelled()) {
                                    throw new SVNCancelException();
                                }
                            }
                        });
                if (changes.size() > 0) {
                    Logger.getLogger().debug("Found changes of branch ''{0}'': {1}", offshoot.getRemotePath(), changes.size());

                    offshoot.setWCLoaded(false);
                    offshoot.model.commit(false);

                    final List<SVNEvent> events = new LinkedList<>();
                    offshoot.getRepository().update(
                            offshoot.getRemotePath(),
                            offshoot.getLocalPath(),
                            revision,
                            new ISVNEventHandler() {
                                private final Function<BinarySource, SVNInfo> getInfo = source -> {
                                    try {
                                        return source.getRepository().getInfo(source.getLocalPath().toFile());
                                    } catch (SVNException e) {
                                        return null;
                                    }
                                };
                                private final Function<SVNInfo, String> fmtInfo = svnInfo ->
                                        svnInfo == null || SVNRevision.UNDEFINED.equals(svnInfo.getRevision()) ?
                                                "<none>" :
                                                MessageFormat.format(
                                                    "{0} / {1}",
                                                    svnInfo.getRevision().getNumber(),
                                                    Offshoot.DATE_FORMAT.format(svnInfo.getCommittedDate())
                                                );
                                private SVNInfo r1, r2;

                                @Override
                                public void handleEvent(SVNEvent svnEvent, double v) throws SVNException {
                                    if (svnEvent.getAction() == SVNEventAction.UPDATE_STARTED) {
                                        r1 = getInfo.apply(offshoot);

                                        Logger.getLogger().info(
                                                "Update [{0}/{1}] started ({2} files to be updated)",
                                                offshoot.getRepository().getPID(),
                                                offshoot.getPID(),
                                                changes.size()
                                        );
                                    } else if (svnEvent.getAction() == SVNEventAction.UPDATE_COMPLETED) {
                                        r2 = getInfo.apply(offshoot);

                                        List<String> details = events.stream().collect(Collectors.groupingBy(event -> {
                                            if (event.getAction() == SVNEventAction.UPDATE_ADD || event.getAction() == SVNEventAction.UPDATE_SHADOWED_ADD)
                                                return "Added";
                                            else if (event.getAction() == SVNEventAction.UPDATE_DELETE || event.getAction() == SVNEventAction.UPDATE_SHADOWED_DELETE)
                                                return "Deleted";
                                            else if (event.getAction() == SVNEventAction.UPDATE_UPDATE || event.getAction() == SVNEventAction.UPDATE_SHADOWED_UPDATE)
                                                return "Changed";
                                            else if (event.getAction() == SVNEventAction.RESTORE)
                                                return "Restored";
                                            else
                                                return "Skipped";
                                        })).entrySet().stream()
                                                .sorted(Comparator.comparingInt(o -> o.getKey().charAt(0)))
                                                .filter(entry -> !entry.getValue().isEmpty())
                                                .map(entry -> String.format(" * %s: %d", StringUtils.rightPad(entry.getKey(), 10), entry.getValue().size()))
                                                .collect(Collectors.toList());
                                        Logger.getLogger().info(
                                                "Update [{0}/{1}] finished (revision: {2} -> {3})\n{4}",
                                                offshoot.getRepository().getPID(),
                                                offshoot.getPID(),
                                                fmtInfo.apply(r1),
                                                fmtInfo.apply(r2),
                                                String.join("\n", details)
                                        );
                                    } else {
                                        if (svnEvent.getNodeKind() == SVNNodeKind.FILE) {
                                            events.add(svnEvent);
                                            setProgress(
                                                    Math.min(events.size() * 100 / changes.size(), 100),
                                                    MessageFormat.format(
                                                            Language.get(UpdateWC.class, "command@progress"),
                                                            offshoot.getRelativePath(svnEvent.getFile().getAbsoluteFile().toPath())
                                                    )
                                            );
                                        }
                                    }
                                }

                                @Override
                                public void checkCancelled() throws SVNCancelException {
                                    checkPaused();
                                    if (UpdateTask.this.isCancelled()) {
                                        throw new SVNCancelException();
                                    }
                                }
                            }
                    );
                } else {
                    Logger.getLogger().info(
                            "Update [{0}/{1}] finished. no need to update",
                            offshoot.getRepository().getPID(),
                            offshoot.getPID()
                    );
                }
            } catch (SVNException e) {
                Optional<Throwable> rootCause = Stream
                        .iterate(e, Throwable::getCause)
                        .filter(element -> element.getCause() == null)
                        .findFirst();
                if (rootCause.isPresent()) {
                    if (rootCause.get() instanceof SVNCancelException || rootCause.get() instanceof ClosedChannelException) {
                        Logger.getLogger().info(
                                "Update [{0}/{1}] canceled",
                                offshoot.getRepository().getPID(),
                                offshoot.getPID()
                        );
                        throw new CancelException();
                    } else {
                        throw e;
                    }
                }
            }
            return null;
        }

        @Override
        public void finished(Void res) {
            offshoot.model.updateDynamicProps();
            WCStatus status = offshoot.getWCStatus();
            offshoot.setWCLoaded(status.equals(WCStatus.Successful));
            try {
                offshoot.model.commit(false);
            } catch (Exception ignore) {}
        }
    }
}