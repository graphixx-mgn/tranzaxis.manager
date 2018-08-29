package manager.commands;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import manager.nodes.Offshoot;
import manager.type.BuildStatus;
import manager.type.WCStatus;
import org.apache.commons.io.FileDeleteStrategy;


public class DeleteWC extends EntityCommand {
    
    public DeleteWC() {
        super(
                "clean", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/minus.png"), 28, 28), 
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
        executeTask(entity, new DeleteTask((Offshoot) entity), true);
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
                StringBuilder entityList = new StringBuilder();
                Arrays.asList(getContext()).forEach((entity) -> {
                    entityList.append("<br>&emsp;&#9913&nbsp;&nbsp;").append(entity.toString());
                });
                message = MessageFormat.format(
                        Language.get("confirm@clean.range"), 
                        entityList.toString()
                );
            }
            MessageBox.show(
                    MessageType.CONFIRMATION, null, message,
                    (close) -> {
                        if (close.getID() == Dialog.OK) {
                            Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), Arrays.asList(getContext()));
                            for (Entity entity : getContext()) {
                                if (!entity.model.existReferencies()) {
                                    execute(entity, null);
                                }
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
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            String wcPath = offshoot.getWCPath();

            setProgress(0, Language.get(DeleteWC.class.getSimpleName(), "command@calc"));
            long totalFiles = Files.walk(Paths.get(wcPath)).count();
            Logger.getLogger().info("Total number of files/directories in working copy ''{0}'': {1}", new Object[] {wcPath, totalFiles});

            AtomicInteger processed = new AtomicInteger(0);
            Files.walkFileTree(Paths.get(wcPath), new SimpleFileVisitor<Path>() {
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    return processPath(file);
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return processPath(dir);
                }
                
                private FileVisitResult processPath(Path path) {
                    checkPaused();
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    try {
                        FileDeleteStrategy.NORMAL.delete(path.toFile());
                        processed.addAndGet(1);
                        String fileName = path.toString().replace(wcPath+File.separator, "");
                        setProgress(
                                (int) (processed.get() * 100 / totalFiles),
                                MessageFormat.format(
                                        Language.get(DeleteWC.class.getSimpleName(), "command@progress"),
                                        fileName
                                )
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
         
            DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(wcPath).getParent());
            if (!dirStream.iterator().hasNext()) {
                FileDeleteStrategy.NORMAL.delete(new File(wcPath).getParentFile());
            }
            return null;
        }

        @Override
        public void finished(Void t) {
            SwingUtilities.invokeLater(() -> {
                WCStatus status = offshoot.getStatus();
                offshoot.model.setValue("wcStatus", status);
                if (!isCancelled()) {
                    offshoot.model.setValue("built", new BuildStatus());
                }
                offshoot.model.commit();
                if (!isCancelled()) {
                    if (offshoot.model.getID() != null) {
                        ((IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class)).removeClassInstance(
                                offshoot.getClass(), offshoot.model.getID()
                        );
                    }
                }
                offshoot.setMode(INode.MODE_SELECTABLE + (status.equals(WCStatus.Absent) ? 0 : INode.MODE_ENABLED));
            });
        }
    
    }
    
}
