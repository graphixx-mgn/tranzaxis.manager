package codex.scheduler;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExecuteJob extends EntityCommand<AbstractJob> {

    private final static ImageIcon ICON_RUN = ImageUtils.getByPath("/images/run_job.png");

    public ExecuteJob() {
        super(
                "execute job",
                null,
                ICON_RUN,
                Language.get(JobScheduler.class, "execute@title"),
                job -> job.model.isValid()
        );
        Function<List<AbstractJob>, CommandStatus> defaultActivator = activator;
        activator = entities -> {
            boolean chosenDuplicateClasses = entities.stream()
                    .collect(Collectors.groupingBy(AbstractJob::getClass, Collectors.counting()))
                    .entrySet().stream().anyMatch(group -> group.getValue() > 1);
            return new CommandStatus(
                    defaultActivator.apply(entities).isActive() && !chosenDuplicateClasses
            );
        };
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(AbstractJob context, Map<String, IComplexType> params) {
        context.executeJob(null, true);
    }
}
