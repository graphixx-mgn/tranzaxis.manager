package manager.commands.common.report;

import codex.explorer.tree.INode;
import codex.model.Entity;
import codex.task.ITask;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import manager.commands.offshoot.DeleteWC;
import manager.nodes.Development;
import manager.nodes.Offshoot;
import java.io.File;
import java.nio.file.Path;
import java.util.StringJoiner;
import java.util.stream.Stream;

@BranchLink(branchCatalogClass = Development.class, priority = 4)
public class SourceEntry extends DirEntry {

    public SourceEntry(EntityRef owner, String filePath) {
        super(owner, ImageUtils.getByPath("/images/branch.png"), filePath);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);

        StringJoiner logPath = new StringJoiner(File.separator);
        logPath.add(getPID());
        logPath.add(".config");
        logPath.add("var");
        logPath.add("log");

        File logDir = new File(logPath.toString());
        if (logDir.exists()) {
            Stream.of(IComplexType.coalesce(logDir.listFiles(), new File[]{})).forEach(file -> {
                if (file.getName().startsWith("heapdump")) {
                    parent.insert(Entity.newInstance(DumpEntry.class, getOwner().toRef(), file.getAbsolutePath()));
                }
            });
        }
    }

    @Override
    protected boolean canDeleteUsed() {
        return false;
    }

    @Override
    public boolean skipDirectory(Path dir) {
        return dir.toFile().getName().equals(".config");
    }

    @Override
    protected ITask createDeleteTask() {
        Offshoot offshoot = (Offshoot) entityRef.getValue();
        return ((DeleteWC) offshoot.getCommand("clean")).new DeleteTask(offshoot) {
            @Override
            public void finished(Void result) {
                super.finished(result);
                if (isCancelled() || new File(getPID()).exists()) {
                    fireChangeEvent();
                    setSize(getActualSize());
                } else {
                    getParent().delete(SourceEntry.this);
                }
            }
        };
    }
}
