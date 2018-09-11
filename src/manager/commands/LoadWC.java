package manager.commands;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import manager.nodes.Development;
import manager.nodes.ReleaseList;
import manager.nodes.Repository;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;


public class LoadWC extends EntityCommand {
    
    private final static ITaskExecutorService TES = (ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class);
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
        activator = (entities) -> {
            if (entities != null && entities.length > 0 && !(entities.length > 1 && !multiContextAllowed())) {
                boolean locked = entities[0].model.getUnsavedValue("locked") == Boolean.TRUE;
                getButton().setIcon(locked ? ENABLED : DISABLED);
                getButton().setEnabled(true);
            } else {
                getButton().setIcon(ENABLED);
                getButton().setEnabled(false);
            }
        };
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {      
        AtomicBoolean locked = new AtomicBoolean(((Repository) entity).model.getValue("locked") == Boolean.TRUE);
        if (getContext() == null) {
            locked.set(!locked.get());
        }
        entity.model.getProperties(Access.Edit).stream()
                .filter((propName) -> {
                    return !propName.equals(EntityModel.PID);
                }).forEach((propName) -> {
                    entity.model.getEditor(propName).setEditable(locked.get());
                });
        if (!locked.get()) {
            load(entity);
        } else {
            unload(entity);
        }
    }
    
    private void load(Entity entity) {
        if (getContext() == null) {
            TES.quietTask(new LoadTask((Repository) entity));
        } else {
            TES.enqueueTask(new LoadTask((Repository) entity));
        }
    }
    
    private void unload(Entity entity) {
        new LinkedList<>(entity.childrenList()).forEach((child) -> {
            entity.delete(child);
        });
        switchLock(entity, false);
    }
    
    private void switchLock(Entity entity, boolean locked) {
        entity.setMode((locked ? INode.MODE_ENABLED : INode.MODE_NONE) + INode.MODE_SELECTABLE);
        entity.model.getProperties(Access.Edit).stream()
                .filter((propName) -> {
                    return !propName.equals(EntityModel.PID);
                }).forEach((propName) -> {
                    entity.model.getEditor(propName).setEditable(!locked);
                });
        entity.model.setValue("locked", locked);
        entity.model.commit();
        entity.getLock().release();
    }
    
    private class LoadTask extends AbstractTask<Boolean> {
        
        final Repository repo;

        public LoadTask(Repository repo) {
            super(MessageFormat.format(
                    Language.get(Catalog.class.getSimpleName(), "task@load"),
                    repo.getPathString()
            ));
            this.repo = repo;
        }

        @Override
        public Boolean execute() throws Exception {
            String rootUrl = (String) repo.model.getValue("repoUrl");
            ISVNAuthenticationManager authMgr = repo.getAuthManager();
            try {
                if (SVN.checkConnection(rootUrl, authMgr)) {
                    boolean valid = SVN.list(rootUrl, authMgr).stream().map((entry) -> {
                        return entry.getName();
                    }).collect(Collectors.toList()).containsAll(DIRS);
                
                    if (!valid) {
                        MessageBox.show(MessageType.ERROR, 
                                MessageFormat.format(
                                        Language.get(Repository.class.getSimpleName(), "error@message"),
                                        repo.model.getPID(),
                                        MessageFormat.format(
                                            Language.get(Repository.class.getSimpleName(), "error@invalid"),
                                            DIRS.stream().collect(Collectors.joining(", "))
                                        )
                                )
                        );
                        return false;
                    } else {
                        repo.insert(new Development(repo.toRef()));
                        repo.insert(new ReleaseList(repo.toRef()));
                        return true;
                    }
                } else {
                    MessageBox.show(MessageType.WARNING, 
                            MessageFormat.format(
                                    Language.get(Repository.class.getSimpleName(), "error@message"),
                                    repo.model.getPID(),
                                    Language.get(Repository.class.getSimpleName(), "error@auth")
                            )
                    );
                    return false;
                }
            } catch (SVNException e) {
                SVNErrorCode code = e.getErrorMessage().getErrorCode();
                if (code == SVNErrorCode.RA_SVN_IO_ERROR || code == SVNErrorCode.RA_SVN_MALFORMED_DATA) {
                    repo.insert(new Development(repo.toRef()));
                    return true;
                } else {
                    MessageBox.show(MessageType.ERROR, 
                            MessageFormat.format(
                                    Language.get(Repository.class.getSimpleName(), "error@message"),
                                    repo.model.getPID(),
                                    e.getMessage()
                            )
                    );
                    return false;
                }
            }
        }

        @Override
        public void finished(Boolean result) {
            switchLock(repo, result);
        }
    
    }
    
}
