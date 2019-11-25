package manager.commands.common.report;

import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.common.DiskUsageReport;
import org.apache.commons.io.FileDeleteStrategy;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

@BranchLink(priority = 2)
public class DirEntry extends Entry {

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    public DirEntry(EntityRef owner, String filePath) {
        this(owner, ImageUtils.getByPath("/images/unknown_dir.png"), filePath);
    }

    protected DirEntry(EntityRef owner, ImageIcon icon, String filePath) {
        super(owner, icon, filePath);
    }

    private long getFilesCount() throws IOException {
        return Files.walk(new File(getPID()).toPath()).count();
    }

    @Override
    protected void deleteEntry() {
        TES.executeTask(createDeleteTask());
    }

    protected ITask createDeleteTask() {
        return new DeleteDirectory(this);
    }


    class DeleteDirectory extends AbstractTask<Void> {

        private final DirEntry entry;

        DeleteDirectory(DirEntry entry) {
            super(Language.get(DiskUsageReport.class, "delete@title") + ": " + entry.getPID());
            this.entry = entry;
        }

        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            setProgress(0, Language.get(DiskUsageReport.class, "delete@calc"));
            long totalFiles = entry.getFilesCount();
            File directory  = new File(getPID());

            AtomicInteger processed = new AtomicInteger(0);
            Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    return processPath(file);
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
                        String fileName = path.toString().replace(entry.getPID() + File.separator, "");
                        setProgress(
                                (int) (processed.get() * 100 / totalFiles),
                                MessageFormat.format(
                                        Language.get(DiskUsageReport.class, "delete@progress"),
                                        fileName
                                )
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory.getParentFile().toPath())) {
                if (!dirStream.iterator().hasNext()) {
                    directory.getParentFile().delete();
                }
            }
            return null;
        }

        @Override
        public void finished(Void result) {
            if (isCancelled() || new File(getPID()).exists()) {
                try {
                    entry.setSize(entry.getActualSize());
                } catch (IOException ignore) {
                    //
                }
            } else {
                entry.getParent().detach(entry);
            }
        }
    }
}
