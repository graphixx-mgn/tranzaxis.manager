package manager.commands.repository;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Catalog;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.Repository;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import javax.swing.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;

public class LoadWC extends EntityCommand<Repository> {
    
    private final static ImageIcon ENABLED  = ImageUtils.getByPath("/images/switch_on.png");
    private final static ImageIcon DISABLED = ImageUtils.getByPath("/images/switch_off.png");

    public LoadWC() {
        super(
                "load", 
                Language.get(Repository.class, "command@load"),
                DISABLED, 
                Language.get(Repository.class, "command@load"),
                null
        );
        activator = entities -> {
            if (entities != null && entities.size() > 0 && !(entities.size() > 1 && !multiContextAllowed())) {
                return new CommandStatus(true, entities.get(0).isLocked(true) ? ENABLED : DISABLED);
            } else {
                return new CommandStatus(false, ENABLED);
            }
        };
    }

    @Override
    public void execute(Repository repository, Map<String, IComplexType> map) {
        if (repository.isLocked(true)) {
            unload(repository);
        } else {
            load(repository);
        }
    }
    
    public void load(Repository repository) {
        repository.setLocked(true);
        ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);
        if (!getContext().isEmpty()) {
            TES.executeTask(new LoadTask(repository));
        } else {
            TES.enqueueTask(new LoadTask(repository));
        }
    }
    
    private void unload(Repository repository) {
        repository.setLocked(false);
        repository.unloadBranches();
        switchLock(repository, false);
    }
    
    private void switchLock(Repository repository, boolean locked) {
        repository.setMode((locked ? INode.MODE_ENABLED : INode.MODE_NONE) + INode.MODE_SELECTABLE);
        repository.setLocked(locked);
        try {
            repository.model.commit(false);
        } catch (Exception e) {
            //
        }
    }
    
    private class LoadTask extends AbstractTask<Boolean> {
        
        final Repository repository;

        LoadTask(Repository repository) {
            super(MessageFormat.format(
                    Language.get(Catalog.class, "task@load"),
                    repository.getPathString()
            ));
            this.repository = repository;
        }

        @Override
        public Boolean execute() throws Exception {
            String rootUrl = repository.getRepoUrl();
            ISVNAuthenticationManager authMgr = repository.getAuthManager();
            try {
                if (SVN.checkConnection(rootUrl, authMgr)) {
                    try {
                        String repositorySystemName = repository.getSystemName();
                        if (repositorySystemName != null) {
                            Logger.getLogger().debug("Remote repository ''{0}'' verified successfully [name = ''{1}'']", repository, repositorySystemName);
                            repository.loadBranches();
                            Logger.getLogger().info("Repository ''{0}'' loaded in ONLINE mode", repository);
                            return true;
                        }
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                            MessageBox.show(MessageType.ERROR,
                                MessageFormat.format(
                                        Language.get(Repository.class, "error@invalid"),
                                        repository.getRepoUrl()
                                )
                            );
                            return false;
                        } else {
                            throw e;
                        }
                    }
                }
            } catch (SVNException | IOException e) {
                if (
                        e instanceof IOException ||
                        Arrays.asList(SVNErrorCode.RA_SVN_IO_ERROR, SVNErrorCode.RA_SVN_MALFORMED_DATA).contains(
                                ((SVNException) e).getErrorMessage().getErrorCode()
                        )
                ) {
                    repository.loadBranches();
                    Logger.getLogger().warn("Repository ''{0}'' loaded in OFFLINE mode: {1}", repository, e.getMessage());
                    return true;
                } else {
                    if (getContext().isEmpty()) {
                        Logger.getLogger().warn("Repository ''{0}'' not loaded. Reason: {1}", repository, e.getMessage());
                    } else {
                        Logger.getLogger().warn("Repository ''{0}'' not loaded. Reason: {1}", repository, e.getMessage());
                        MessageBox.show(
                                MessageType.WARNING,
                                Repository.formatErrorMessage(
                                        MessageFormat.format(
                                                Language.get(Repository.class, "fail@connect"),
                                                repository.getPID()
                                        ), e
                                )
                        );
                    }
                    return false;
                }
            }
            return false;
        }

        @Override
        public void finished(Boolean result) {
            switchLock(repository, result);
        }
    
    }
    
}
