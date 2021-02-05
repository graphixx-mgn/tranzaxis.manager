package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.type.IComplexType;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            builder.directory(workDir);
            builder.start();
        } catch (IOException e) {
            Logger.getLogger().error("Unable to execute RW Designer", e);
        }
    }

    private File getDesignerExecFile(Offshoot offshoot) {
        return new File(String.join(
                File.separator,
                offshoot.getLocalPath(),
                "org.radixware", "kernel", "designer", "bin", "bin",
                Runtime.OS.isWindows.get() ? "designer"+(Runtime.OS.is64bit.get() ? "64" : "")+".exe" : "designer"
        ));
    }

    private List<String> getDesignerCommand(Offshoot offshoot) {
        return new ArrayList<String>() {{
            File fileRun = getDesignerExecFile(offshoot);
            File confDir = new File(offshoot.getLocalPath().concat(File.separator).concat(".config"));
            if (Runtime.OS.isWindows.get()) {
                add(FileUtils.pathQuotation(fileRun.getAbsolutePath()));
            } else {
                add("sh");
                add(FileUtils.pathQuotation(fileRun.getAbsolutePath()));
            }
            addAll(offshoot.getJvmDesigner().stream().map("-J"::concat).collect(Collectors.toList()));
            add("--userdir");
            add(FileUtils.pathQuotation(confDir.getAbsolutePath()));
        }};
    }
    
}
