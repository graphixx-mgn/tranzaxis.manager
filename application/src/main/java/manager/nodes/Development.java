package manager.nodes;

import codex.explorer.tree.INode;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@RepositoryBranch.Branch(remoteDir = "dev", localDir = "sources", hasArchive = false)
public class Development extends RepositoryBranch {

    private final static String PROP_JVM_DESIGNER = "jvmDesigner";

    public Development(EntityRef owner) {
        this(owner, null);
    }

    public Development(EntityRef owner, String PID) {
        super(owner, ImageUtils.getByPath("/images/development.png"), PID, null);
        model.addUserProp(PROP_JVM_DESIGNER, new ArrStr("-Xmx6G"), false, Access.Select);

        setChildFilter(Filter.class);
    }

    @Override
    public void detach(INode child) {
        // Do not delete from tree
    }

    private void detachForce(INode child) {
        super.detach(child);
    }

    @Override
    public void loadBranch() {
        getRepository().attach(this);
    }

    @Override
    public void unloadBranch() {
        new LinkedList<>(childrenList()).forEach(this::detachForce);
        getRepository().detach(this);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Offshoot.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @SuppressWarnings("unchecked")
    final List<String> getJvmDesigner() {
        return (List<String>) model.getValue(PROP_JVM_DESIGNER);
    }

    public static <E extends Entity> void deleteInstance(E entity, boolean cascade, boolean confirmation) {
        Entity.deleteInstance(entity, true, true);
    }


    enum Filter implements IFilter {

        HidePatches((parent, child) -> {
            final Map<Integer, List<Integer>> disperseMap = parent.childrenList().stream()
                    .map(iNode -> (Entity) iNode)
                    .map(Entity::getPID)
                    .map(pid -> pid.split("\\.").length)
                    .collect(Collectors.groupingBy(length -> length));
            final float average = 100 / (float) disperseMap.size();
            List<Integer> candidates = new ArrayList<>();
            disperseMap.forEach((integer, integers) -> {
                float weight = integers.size() * 100 / (float) parent.getChildCount();
                if (weight >= average) {
                    candidates.add(integer);
                }
            });
            Collections.sort(candidates);
            int verNum = candidates.size() == 1 ? candidates.get(0) : candidates.get(candidates.size()-2);
            return child.getPID().equals(BinarySource.TRUNK) || child.getPID().split("\\.").length <= verNum;
        }),
        ShowAll((parent, child) -> true);

        private final BiPredicate<Entity, Entity> condition;
        private final String title = Language.get(Development.class, "filter@"+name().toLowerCase()+PropertyHolder.PROP_NAME_SUFFIX);

        Filter(BiPredicate<Entity, Entity> condition) {
            this.condition = condition;
        }

        @Override
        public BiPredicate<Entity, Entity> getCondition() {
            return condition;
        }


        @Override
        public String toString() {
            return title;
        }
    }
}
