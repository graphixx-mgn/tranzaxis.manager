package manager.job;

import codex.mask.EntityFilter;
import codex.model.EntityDefinition;
import codex.task.ITask;
import codex.type.Bool;
import codex.type.EntityRef;
import manager.commands.offshoot.RefreshWC;
import manager.nodes.Offshoot;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@EntityDefinition(title = "class@title", icon="/images/rebuild.png")
public class RefreshWorkingCopies extends DevelopmentJob {

    private static final String PROP_OFFSHOOT_LIST = "offshoots";

    public RefreshWorkingCopies(EntityRef owner, String title) {
        super(owner, title);

        Map<Offshoot, Boolean> offshoots = new LinkedHashMap<>();

        model.addUserProp(PROP_OFFSHOOT_LIST,
                new codex.type.Map<>(
                        new EntityRef<>(Offshoot.class).setMask(new EntityFilter<>(offshoot -> offshoot.getID() != null)),
                        new Bool(true),
                        offshoots
                ), true, null);
    }

    @SuppressWarnings("unchecked")
    private Map<Offshoot, Boolean> getOffshoots() {
        return (Map<Offshoot, Boolean>) model.getValue(PROP_OFFSHOOT_LIST);
    }

    @Override
    protected Collection<ITask> getTasks() {
        Map<Offshoot, Boolean> offshoots = getOffshoots();
        return offshoots.entrySet().stream()
                .map(entry -> entry.getKey().getCommand(RefreshWC.class).getTask(
                        entry.getKey(),
                        Collections.singletonMap("clean", new Bool(entry.getValue()))
                ))
                .collect(Collectors.toList());
    }
}
