package manager.commands.offshoot.build;

import org.radixware.kernel.common.check.RadixProblem;
import javax.swing.ImageIcon;

public class CompilerEvent {

    private final RadixProblem.ESeverity severity;
    private final String defId;
    private final String name;
    private final ImageIcon icon;
    private final String message;

    public CompilerEvent(
            RadixProblem.ESeverity severity,
            String defId,
            String name,
            ImageIcon icon,
            String message
    ) {
        this.severity = severity;
        this.defId = defId;
        this.name = name;
        this.icon = icon;
        this.message = message;
    }

    public RadixProblem.ESeverity getSeverity() {
        return severity;
    }

    public String getDefId() {
        return defId;
    }

    public String getName() {
        return name;
    }

    public ImageIcon getIcon() {
        return icon;
    }

    public String getMessage() {
        return message;
    }

}
