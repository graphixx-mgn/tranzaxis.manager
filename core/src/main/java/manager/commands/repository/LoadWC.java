package manager.commands.repository;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Catalog;
import codex.task.AbstractTask;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import manager.nodes.Development;
import manager.nodes.ReleaseList;
import manager.nodes.Repository;
import static manager.nodes.Repository.*;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;


public class LoadWC extends EntityCommand<Repository> {
    
    private final static ImageIcon ENABLED  = ImageUtils.resize(ImageUtils.getByPath("/images/switch_on.png"),  28, 28);
    private final static ImageIcon DISABLED = ImageUtils.resize(ImageUtils.getByPath("/images/switch_off.png"), 28, 28);
    private final static List<String> DIRS  = Arrays.asList(new String[] {"releases", "dev"});

    public LoadWC() {
        super(
                "load", 
                Language.get(Repository.class.getSimpleName(), "command@load"), 
                DISABLED, 
                Language.get(Repository.class.getSimpleName(), "command@load"), 
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
        boolean load = getContext().isEmpty() ? 
                repository.isLocked(true) : 
                !repository.isLocked(true);
        if (load) {
            load(repository);
        } else {
            unload(repository);
        }
    }
    
    private void load(Repository repository) {
        executeTask(repository, new LoadTask(repository), !getContext().isEmpty());
    }
    
    private void unload(Repository repository) {
        new LinkedList<>(repository.childrenList()).forEach((child) -> {
            repository.delete(child);
        });
        switchLock(repository, false);
    }
    
    private void switchLock(Repository repository, boolean locked) {
        repository.setMode((locked ? INode.MODE_ENABLED : INode.MODE_NONE) + INode.MODE_SELECTABLE);
        
        repository.model.getEditor(PROP_REPO_URL).setEditable(!locked);
        repository.model.getEditor(PROP_AUTH_MODE).setEditable(!locked);
        repository.model.getEditor(PROP_SVN_USER).setEditable(!locked);
        repository.model.getEditor(PROP_SVN_PASS).setEditable(!locked);

        repository.setLocked(locked);
        try {
            repository.model.commit(false);
        } catch (Exception e) {}
    }
    
    private class LoadTask extends AbstractTask<Boolean> {
        
        final Repository repository;

        public LoadTask(Repository repository) {
            super(MessageFormat.format(
                    Language.get(Catalog.class.getSimpleName(), "task@load"),
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
                    boolean valid = SVN.list(rootUrl, authMgr).stream().map((entry) -> {
                        return entry.getName();
                    }).collect(Collectors.toList()).containsAll(DIRS);

                    if (!valid) {
                        MessageBox.show(MessageType.ERROR,
                                MessageFormat.format(
                                        Language.get(Repository.class.getSimpleName(), "error@message"),
                                        repository.getPID(),
                                        MessageFormat.format(
                                            Language.get(Repository.class.getSimpleName(), "error@invalid"),
                                            DIRS.stream().collect(Collectors.joining(", "))
                                        )
                                )
                        );
                        return false;
                    } else {
                        repository.insert(new Development(repository.toRef()));
                        repository.insert(new ReleaseList(repository.toRef()));
                        Logger.getLogger().info("Repository ''{0}'' loaded in ONLINE mode", repository);
                        return true;
                    }
                } else {
                    if (getContext() == null) {
                        Logger.getLogger().warn(
                                "Repository ''{0}'' not loaded. Reason: {1}", repository, 
                                Language.get(Repository.class.getSimpleName(), "error@auth", Locale.US)
                        );
                    } else {
                        MessageBox.show(MessageType.WARNING, 
                                MessageFormat.format(
                                        Language.get(Repository.class.getSimpleName(), "error@message"),
                                        repository.getPID(),
                                        Language.get(Repository.class.getSimpleName(), "error@auth")
                                )
                        );
                    }
                    return false;
                }
            } catch (SVNException e) {
                SVNErrorCode code = e.getErrorMessage().getErrorCode();
                if (code == SVNErrorCode.RA_SVN_IO_ERROR || code == SVNErrorCode.RA_SVN_MALFORMED_DATA) {
                    repository.insert(new Development(repository.toRef()));
                    Logger.getLogger().warn("Repository ''{0}'' loaded in OFFLINE mode", repository);
                    return true;
                } else {
                    if (getContext() == null) {
                        Logger.getLogger().warn("Repository ''{0}'' not loaded. Reason: {1}", repository, e.getErrorMessage().getMessage());
                    } else {
                        MessageBox.show(MessageType.ERROR, 
                                MessageFormat.format(
                                        Language.get(Repository.class.getSimpleName(), "error@message"),
                                        repository.getPID(),
                                        e.getMessage()
                                )
                        );
                    }
                    return false;
                }
            }
        }

        @Override
        public void finished(Boolean result) {
            switchLock(repository, result);
        }
    
    }
    
}
