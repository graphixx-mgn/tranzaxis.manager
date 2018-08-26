package manager.commands;

import codex.command.EntityCommand;
import codex.explorer.tree.INode;
import codex.model.Catalog;
import codex.model.Entity;
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
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import manager.nodes.Development;
import manager.nodes.Repository;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;


public class LoadWC extends EntityCommand {
    
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
        boolean locked = ((Repository) entity).model.getValue("locked") == Boolean.TRUE;
        if (getContext() == null) {
            locked = !locked;
        }
        if (!locked) {
            load(entity);
        } else {
            unload(entity);
        }
    }
    
    private void load(Entity entity) {
        ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).enqueueTask(
                new LoadTask((Repository) entity)
        );
    }
    
    private void unload(Entity entity) {
        new LinkedList<>(entity.childrenList()).forEach((child) -> {
            entity.delete(child);
        });
        switchLock(entity, false);
    }
    
    private void switchLock(Entity entity, boolean locked) {
        entity.setMode((locked ? INode.MODE_ENABLED : INode.MODE_NONE) + INode.MODE_SELECTABLE);
        entity.model.getEditor("repoUrl").setEditable(!locked);
        entity.model.setValue("locked", locked);
        entity.model.commit();
        entity.getLock().release();
    }
    
    private class LoadTask extends AbstractTask<Void> {
        
        final Repository repo;

        public LoadTask(Repository repo) {
            super(MessageFormat.format(
                    Language.get(Catalog.class.getSimpleName(), "task@load"),
                    repo.getPathString()
            ));
            this.repo = repo;
        }

        @Override
        public Void execute() throws Exception {
            String rootUrl = (String) repo.model.getValue("repoUrl");
            String svnUser = (String) repo.model.getValue("svnUser");
            String svnPass = (String) repo.model.getValue("svnPass");
            
            try {
                boolean valid = SVN.list(rootUrl, svnUser, svnPass).stream().map((entry) -> {
                    return entry.getName();
                }).collect(Collectors.toList()).containsAll(DIRS);
                
                if (!valid) {
                    throw new UnsupportedOperationException(Language.get(Repository.class.getSimpleName(), "error@invalid"));
                } else {
                    //ReleaseList releaseRoot = new ReleaseList(repo.toRef());
                    Development development = new Development(repo.toRef());
                }
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode().getCode() != SVNErrorCode.RA_SVN_MALFORMED_DATA.getCode()) {
                    throw new UnsupportedOperationException(Language.get(Repository.class.getSimpleName(), "error@svnerr"));
                } else {
                    Development development = new Development(repo.toRef());
                }
            }
            return null;
        }

        @Override
        public void finished(Void result) {
            switchLock(repo, true);
        }
    
    }
    
}
