package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import codex.utils.Runtime;
import manager.nodes.Offshoot;

public class RunDesigner extends EntityCommand<Offshoot> {

    public RunDesigner() {
        super(
                "designer",
                Language.get("title"),
                ImageUtils.getByPath("/images/designer.png"),
                Language.get("desc"), 
                (offshoot) -> offshoot.getWCStatus().isOperative()
        );
    }

    @Override
    public void execute(Offshoot offshoot, Map<String, IComplexType> map) {
        try {
            File fileRun = getDesignerExecFile(offshoot);
            File workDir = fileRun.getParentFile();
            List<String> cmdList = getDesignerCommand(offshoot);
            
            ProcessBuilder builder = new ProcessBuilder(cmdList);
            builder.inheritIO();
            builder.directory(workDir);
            builder.start();
        } catch (IOException e) {
            Logger.getLogger().error("Unable to execute RW Designer", e);
        }
    }

    private File getDesignerExecFile(Offshoot offshoot) {
        return offshoot.getLocalPath()
                .resolve("org.radixware")
                .resolve("kernel")
                .resolve("designer")
                .resolve("bin")
                .resolve("bin")
                .resolve(Runtime.OS.isWindows.get() ? "designer"+(Runtime.OS.is64bit.get() ? "64" : "")+".exe" : "designer")
                .toFile();
    }

    private List<String> getDesignerCommand(Offshoot offshoot) {
        return new ArrayList<String>() {{
            File fileRun = getDesignerExecFile(offshoot);
            File confDir = offshoot.getLocalPath().resolve(".config").toFile();
            if (!Runtime.OS.isWindows.get()) {
                add("sh");
            }
            add(fileRun.getAbsolutePath());
            addAll(offshoot.getJvmDesigner().stream().map("-J"::concat).collect(Collectors.toList()));
            addAll(offshoot.getDesignerProp());
            add("--userdir");
            add(confDir.getAbsolutePath());
            Logger.getLogger().debug("Designer command: " + this);
        }};
    }
}