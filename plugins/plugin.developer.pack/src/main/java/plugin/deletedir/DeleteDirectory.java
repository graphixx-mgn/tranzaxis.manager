package plugin.deletedir;

import codex.mask.DirMask;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.type.FilePath;
import codex.type.IComplexType;
import codex.utils.Language;
import manager.commands.common.DiskUsageReport;
import manager.nodes.Common;
import org.apache.commons.io.FileDeleteStrategy;
import plugin.command.CommandPlugin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DeleteDirectory extends CommandPlugin<Common> {

    private final static String PROP_PATH = "path";

    public DeleteDirectory() {
        super(common -> true);
        setParameters(new PropertyHolder<>(
                PROP_PATH,
                new FilePath(null).setMask(new DirMask()),
                true
        ));
    }

    @Override
    public void execute(Common context, Map<String, IComplexType> params) {
        ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).executeTask(
                new DeleteTask(((FilePath) params.get(PROP_PATH)).getValue())
        );
    }

    static class DeleteTask extends AbstractTask<Void> {

        private final Path path;

        DeleteTask(Path path) {
            super(MessageFormat.format("{0}: {1}", Language.get(DeleteDirectory.class, "title"), path));
            this.path = path;
        }

        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            setProgress(0, Language.get(DiskUsageReport.class, "delete@calc"));
            long totalFiles = Files.walk(path).count();

            AtomicInteger processed = new AtomicInteger(0);
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

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
                        setProgress(
                                (int) (processed.get() * 100 / totalFiles),
                                MessageFormat.format(Language.get(DiskUsageReport.class, "delete@progress"), path)
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return null;
        }
    }
}