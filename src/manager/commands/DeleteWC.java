package manager.commands;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import org.apache.commons.io.FileDeleteStrategy;


public class DeleteWC extends EntityCommand {
    
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);

    public DeleteWC() {
        super(
                "clean", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/clean.png"), 28, 28), 
                Language.get("desc"), 
                (entity) -> {
                    return !entity.model.getValue("wcStatus").equals(WCStatus.Absent);
                }
        );
    }
    
    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).executeTask(
                new DeleteTask((Offshoot) entity)
        );
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        SwingUtilities.invokeLater(() -> {
            String message; 
            if (getContext().length == 1) {
                message = MessageFormat.format(
                        Language.get("confirm@clean.single"), 
                        getContext()[0]
                );
            } else {
                StringBuilder msgBuilder = new StringBuilder(
                        Language.get("confirm@clean.range")
                );
                Arrays.asList(getContext()).forEach((entity) -> {
                    msgBuilder.append("<br>&emsp;&#9913&nbsp;&nbsp;").append(entity.toString());
                });
                message = msgBuilder.toString();
            }
            MessageBox.show(
                    MessageType.CONFIRMATION, null, message,
                    (close) -> {
                        if (close.getID() == Dialog.OK) {
                            Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), Arrays.asList(getContext()));
                            for (Entity entity : getContext()) {
                                execute(entity, null);
                            }
                            activate();
                        }
                    }
            );
        });
    }
    
    
    
    private class DeleteTask extends AbstractTask<Void> {
        
        private final Offshoot offshoot;

        public DeleteTask(Offshoot offshoot) {
            super(Language.get(DeleteWC.class.getSimpleName(), "title") + ": "+offshoot.getWCPath());
            this.offshoot = offshoot;
        }

        @Override
        public Void execute() throws Exception {
            String wcPath = offshoot.getWCPath();
            
            setProgress(0, Language.get(DeleteWC.class.getSimpleName(), "command@calc"));
            List<Path> paths = Files.walk(Paths.get(wcPath)).collect(Collectors.toList());
            long totalFiles = paths.size();
            Logger.getLogger().info("Total number of files/directories in working copy ''{0}'': {1}", new Object[] {wcPath, totalFiles});
            
            AtomicInteger processed = new AtomicInteger(0);
            Files.walk(Paths.get(wcPath)).sorted(Collections.reverseOrder()).collect(Collectors.toList()).forEach((path) -> {
                if (isCancelled()) {
                    return;
                }
                try {
                    FileDeleteStrategy.NORMAL.delete(path.toFile());
                    processed.addAndGet(1);
                    String fileName = path.toString().replace(wcPath+File.separator, "");
                    setProgress(
                            (int) (processed.get() * 100 / totalFiles), 
                            Language.get(DeleteWC.class.getSimpleName(), "command@progress")+fileName
                    );
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return null;
        }

        @Override
        public void finished(Void t) {
            WCStatus status = offshoot.getStatus();
            offshoot.model.setValue("wcStatus", status);
            offshoot.setMode(INode.MODE_SELECTABLE + (status.equals(WCStatus.Absent) ? 0 : INode.MODE_ENABLED));
        }
    
    }
    
}
