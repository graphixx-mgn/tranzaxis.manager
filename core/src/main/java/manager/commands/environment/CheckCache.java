package manager.commands.environment;


import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.utils.Language;
import manager.nodes.Environment;
import manager.nodes.Release;
import manager.nodes.Repository;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;

import javax.swing.*;
import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;

class CheckCache extends AbstractTask<Void> {

    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    private final Environment environment;
    private final ITask[] planningTasks;

    CheckCache(Environment environment, ITask... planningTasks) {
        super(MessageFormat.format(
                Language.get(Release.class.getSimpleName(), "cache@check"),
                environment.getBinaries().getLocalPath()
        ));
        this.environment = environment;
        this.planningTasks = planningTasks;
    }

    @Override
    public Void execute() throws Exception {
        Release release  = (Release) environment.getBinaries();
        try {
            release.getLock().acquire();
        } catch (InterruptedException e) {}

        String  topLayer = environment.getLayerUri(false);
        String  rootUrl  = release.getRemotePath();
        ISVNAuthenticationManager authMgr = release.getRepository().getAuthManager();
        boolean online = false;
        try {
            if (SVN.checkConnection(rootUrl, authMgr)) {
                online = true;
            }
        } catch (SVNException e) {
            SVNErrorCode code = e.getErrorMessage().getErrorCode();
            if (code != SVNErrorCode.RA_SVN_IO_ERROR && code != SVNErrorCode.RA_SVN_MALFORMED_DATA) {
                MessageBox.show(MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(Repository.class.getSimpleName(), "error@message"),
                                release.getRepository().getPID(),
                                e.getMessage()
                        )
                );
                release.getLock().release();
            }
        }

        Map<String, Path> requiredLayers = release.getRequiredLayers(topLayer, online);
        String lostLayer = requiredLayers.entrySet().stream()
                .filter((entry) -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (lostLayer != null) {
            MessageBox.show(MessageType.WARNING,
                    MessageFormat.format(Language.get("RunTX", "error@layer"), lostLayer)
            );
            release.getLock().release();
        }

        boolean checkResult;
        try {
            checkResult = requiredLayers.keySet().parallelStream()
                    .allMatch((layerName) -> Release.checkStructure(
                            release.getLocalPath() + File.separator + layerName + File.separator + "directory.xml",
                            this::isCancelled
                    ));
        } catch (CancelException e) {
            release.getLock().release();
            return null;
        }

        if (!checkResult) {
            if (!online) {
                MessageBox.show(MessageType.WARNING, Language.get("RunTX", "error@structure"));
                release.getLock().release();
            } else {
                    TES.executeTask(
                        release.new LoadCache(new LinkedList<>(requiredLayers.keySet())) {

                            @Override
                            public Void execute() throws Exception {
                                try {
                                    return super.execute();
                                } finally {
                                    release.getLock().release();
                                }
                            }

                            @Override
                            public void finished(Void t) {
                                super.finished(t);
                                if (!isCancelled() && !isFailed()) {
                                    Arrays.asList(planningTasks).forEach(TES::enqueueTask);
                                }
                            }
                        }
                    );
            }
        } else {
            release.getLock().release();
            Arrays.asList(planningTasks).forEach(TES::enqueueTask);
        }
        return null;
    }

    @Override
    public void finished(Void result) {}
}
