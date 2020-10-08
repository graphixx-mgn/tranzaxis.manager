package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import com.sun.javafx.PlatformUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import manager.nodes.Offshoot;
import manager.type.WCStatus;

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
            boolean is64Bits = System.getProperty("sun.cpu.isalist").contains("64");
            String localPath = offshoot.getLocalPath();
            
            StringJoiner designerPath = new StringJoiner(File.separator);
            designerPath.add(localPath);
            designerPath.add("org.radixware");
            designerPath.add("kernel");
            designerPath.add("designer");
            designerPath.add("bin");
            designerPath.add("bin");
            if (PlatformUtil.isWindows()) {
                designerPath.add("designer"+(is64Bits ? "64" : "")+".exe");
            } else {
                designerPath.add("designer");
            }
            
            File designer = new File(designerPath.toString());
            File workDir  = designer.getParentFile();
            File confDir  = new File(localPath+File.separator+".config");
            
            List<String> args = new ArrayList<String>() {{
                if (PlatformUtil.isWindows()) {
                    add(designer.getAbsolutePath());
                } else {
                    add("/bin/sh");
                    add(designer.getAbsolutePath());
                }
                addAll(offshoot.getJvmDesigner().stream().map("-J"::concat).collect(Collectors.toList()));
                add("--userdir");
                add(confDir.toString());
            }};
            
            ProcessBuilder builder = new ProcessBuilder(args);
            builder.directory(workDir);
            builder.start();
        } catch (IOException e) {
            Logger.getLogger().error("Unable to execute RW Designer", e);
        }
    }
    
}
