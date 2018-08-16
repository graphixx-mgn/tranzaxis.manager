package manager.commands;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.model.Entity;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import com.sun.javafx.PlatformUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import manager.nodes.Offshoot;
import manager.type.WCStatus;

/**
 *
 * @author igredyaev
 */
public class RunDesigner extends EntityCommand {

    public RunDesigner() {
        super(
                "designer", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/designer.png"), 28, 28), 
                Language.get("desc"), 
                (entity) -> {
                    return entity.model.getValue("wcStatus").equals(WCStatus.Succesfull);
                }
        );
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        try {
            String localPath = ((Offshoot) entity).getWCPath();
            
            List<String> args = new ArrayList() {{
                boolean is64Bits  = System.getProperty("sun.cpu.isalist").contains("64");
                if (PlatformUtil.isWindows()) {
                    add(localPath+"/org.radixware/kernel/designer/bin/bin/designer"+(is64Bits ? "64" : "")+".exe");
                } else {
                    add("/bin/sh");
                    add("designer");
                }
                add("-J-Xmx4G");
                add("-J-Xms500M");
                add("--console");
                add("suppress");
            }};
            
            ProcessBuilder procBuilder = new ProcessBuilder(args);
            procBuilder.directory(new File(localPath+"/org.radixware/kernel/designer/bin/bin"));
            procBuilder.start();
        } catch (IOException e) {
            Logger.getLogger().error("Unable to execute RW Designer", e);
        }
    }
    
}
