package manager.nodes;

import codex.command.EditorCommand;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.mask.FileMask;
import codex.mask.RegexMask;
import codex.mask.StrSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.task.TaskManager;
import codex.type.ArrStr;
import codex.type.Bool;
import codex.type.FilePath;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNDirEntry;

public class Repository extends Entity {
    
    private static final String AUTH_PASS = "SVN password";
    private static final String AUTH_KEY  = "SSH key file";
    
    private final static ImageIcon LOCKED   = ImageUtils.resize(ImageUtils.getByPath("/images/lock.png"),  18, 18);
    private final static ImageIcon UNLOCKED = ImageUtils.resize(ImageUtils.getByPath("/images/unlock.png"), 18, 18);
    
    private static final Comparator<SVNDirEntry> VERSION_SORTER = new Comparator<SVNDirEntry>() {
        @Override
        public int compare(SVNDirEntry prev, SVNDirEntry next) {
            if (prev.getName() == "" || next.getName() == "") {
                return 0;
            }
            String[] components1 = prev.getName().split("\\.");
            String[] components2 = next.getName().split("\\.");
            int length = Math.min(components1.length, components2.length);
            for(int i = 0; i < length; i++) {
                int result = new Integer(components1[i]).compareTo(Integer.parseInt(components2[i]));
                if(result != 0) {
                    return result;
                }
            }
            return Integer.compare(components1.length, components2.length);
        }
    };
    
    public Repository(INode parent, String title) {
        super(parent, ImageUtils.getByPath("/images/repository.png"), title, null);
        
        PropertyHolder propHolder = new PropertyHolder(
                "repoUrl", 
                new Str(null)
                        .setMask(
                            new RegexMask("svn(|\\+[\\w]+)://[\\w\\./\\d]+", "Invalid SVN url")
                        ), 
                true) 
        {
            @Override
            public boolean isValid() {
                return (Boolean) Repository.this.model.getValue("lockStatus");
            }
        };
        List<String> authTypes = Arrays.asList(new String[] {"", AUTH_PASS, AUTH_KEY});
        
        model.addUserProp(propHolder, null);
        model.setPropUnique(propHolder.getName());
        model.addUserProp("svnUser", new Str(null), true, Access.Select);
        model.addUserProp("svnAuthType", new ArrStr(authTypes).setMask(
                new StrSetMask()
        ), true, Access.Select);
        model.addUserProp("svnPass", new Str(null), false, Access.Select);
        model.addUserProp("svnKeyFile", new FilePath(null).setMask(new FileMask()), false, Access.Select);
        model.addUserProp("lockStatus", new Bool(false), false, Access.Any);
        
        String authType = ((List<String>) model.getValue("svnAuthType")).get(0);
        LockStructure lockCommand = new LockStructure();
        
        model.getEditor("repoUrl").addCommand(lockCommand);
        model.getEditor("repoUrl").setEditable(!(Boolean) model.getValue("lockStatus"));
        model.getEditor("svnPass").setEditable(AUTH_PASS.equals(authType));
        model.getEditor("svnKeyFile").setEditable(AUTH_KEY.equals(authType));
        
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case "svnAuthType":
                    model.getEditor("svnPass").setEditable(AUTH_PASS.equals(newValue.toString()));
                    model.getEditor("svnKeyFile").setEditable(AUTH_KEY.equals(newValue.toString()));
                    break;
            }
        });
        
        if ((Boolean) model.getValue("lockStatus")) {
            lockCommand.load(true);
        }
    }
    
    private class LockStructure extends EditorCommand {
        
        public LockStructure() {
            super(
                    !(Boolean) Repository.this.model.getValue("lockStatus") ? UNLOCKED : LOCKED, 
                    Language.get(Repository.class.getSimpleName(), "command@lock"),
                    (holder) -> {
                        return !holder.isEmpty();
                    }
            );
        }

        @Override
        public void execute(PropertyHolder propHolder) {
            boolean locked = (Boolean) Repository.this.model.getValue("lockStatus");
            if (!locked) {
                load(false);
            } else {
                unload();
            }
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }
        
        private void switchLock(boolean locked) {
            Repository.this.model.getEditor("repoUrl").setEditable(locked);
            getButton().setIcon(locked ? UNLOCKED : LOCKED);
            Repository.this.model.setValue("lockStatus", !locked);
            Repository.this.model.commit();
            ((AbstractEditor) Repository.this.model.getEditor("repoUrl")).updateUI();
        }
        
        private void load(boolean runInBackground) {
            ITask reload = new GroupTask<>(
                "Reload repository: "+Repository.this, 
                new Check(),
                new LoadReleases(),
                new LoadSources()
            );
            reload.addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status status) {
                    if (status == Status.FINISHED) {
                        switchLock(false);
                        SwingUtilities.invokeLater(() -> {
                            Repository.this.setMode(INode.MODE_ENABLED + INode.MODE_SELECTABLE);
                        });
                    }
                }
            });
            Repository.this.setMode(INode.MODE_NONE);
            if (runInBackground) {
                ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).enqueueTask(reload);
            } else {
                ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).executeTask(reload);
            }   
        }
        
        private void unload() {
            new LinkedList<>(Repository.this.childrenList()).forEach((child) -> {
                Repository.this.delete(child);
            });
            switchLock(true);
        }

    }
    
    private class Check extends AbstractTask<Void> {

        public Check() {
            super(Language.get(Repository.class.getSimpleName(), "task@check"));
        }

        @Override
        public Void execute() throws Exception {
            String rootUrl = (String) Repository.this.model.getValue("repoUrl");
            String svnUser = (String) Repository.this.model.getValue("svnUser");
            String svnPass = (String) Repository.this.model.getValue("svnPass");
            
            List<String> required = Arrays.asList(new String[] {"releases", "dev"});
            
            boolean valid = SVN.list(rootUrl, svnUser, svnPass).stream().map((entry) -> {
                return entry.getName();
            }).collect(Collectors.toList()).containsAll(required);
            if (!valid) {
                throw new UnsupportedOperationException(Language.get(Repository.class.getSimpleName(), "error@invalid"));
            }
            return null;
        }

        @Override
        public void finished(Void result) {}
    
    }
    
    private class LoadReleases extends AbstractTask<Void> {

        public LoadReleases() {
            super(Language.get(Repository.class.getSimpleName(), "task@releases"));
        }

        @Override
        public Void execute() throws Exception {
            String rootUrl = (String) Repository.this.model.getValue("repoUrl");
            String svnUser = (String) Repository.this.model.getValue("svnUser");
            String svnPass = (String) Repository.this.model.getValue("svnPass");
            
            List<SVNDirEntry> dirItems = SVN.list(rootUrl+"/releases", svnUser, svnPass);
            
            if (!dirItems.isEmpty()) {
                final AtomicInteger index = new AtomicInteger(0);
                ReleaseList releaseRoot = new ReleaseList(Repository.this);
                dirItems.sort(VERSION_SORTER.reversed());
                
                dirItems.forEach((dirItem) -> {
                    index.incrementAndGet();
                    if (!dirItem.getName().isEmpty()) {
                        Release release = new Release(releaseRoot, dirItem.getName());
                        setProgress(
                                index.get()*100/dirItems.size(), 
                                dirItem.getName()
                        );
                    }
                });
            }
            
            return null;
        }

        @Override
        public void finished(Void result) {}
    
    }
    
    private class LoadSources extends AbstractTask<Void> {

        public LoadSources() {
            super(Language.get(Repository.class.getSimpleName(), "task@sources"));
        }

        @Override
        public Void execute() throws Exception {
            
            String rootUrl = (String) Repository.this.model.getValue("repoUrl");
            String svnUser = (String) Repository.this.model.getValue("svnUser");
            String svnPass = (String) Repository.this.model.getValue("svnPass");
            
            List<SVNDirEntry> dirItems = SVN.list(rootUrl+"/dev", svnUser, svnPass);
            
            if (!dirItems.isEmpty()) {
                final AtomicInteger index = new AtomicInteger(0);
                Development development = new Development(Repository.this);
                Collections.reverse(dirItems);
                
                List<String> existing = development.childrenList().stream().map((child) -> {
                    return ((Entity) child).model.getPID();
                }).collect(Collectors.toList());

                dirItems.forEach((dirItem) -> {
                    index.incrementAndGet();
                    if (!dirItem.getName().isEmpty()) {
                        Offshoot offshoot;
                        if (existing.contains(dirItem.getName())) {
                            offshoot = (Offshoot) development.childrenList().stream().filter((child) -> {
                                return ((Entity) child).model.getPID().equals(dirItem.getName());
                            }).findFirst().get();
                            development.delete(offshoot);
                            development.insert(offshoot);
                        } else {
                            offshoot = new Offshoot(development, dirItem.getName());
                        }
                        offshoot.setMode(INode.MODE_SELECTABLE);
                        offshoot.model.setValue("wcPath", rootUrl+"/dev/"+dirItem.getName());
                        
                        setProgress(
                                index.get()*100/dirItems.size(),
                                dirItem.getName()
                        );
                    }
                });
            }
            
            return null;
        }

        @Override
        public void finished(Void result) {}
    
    }

}
