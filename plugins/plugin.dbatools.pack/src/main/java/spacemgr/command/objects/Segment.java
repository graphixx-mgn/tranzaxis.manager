package spacemgr.command.objects;

import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public abstract class Segment {

    private final List<Extent> extents = new LinkedList<>();
    private final String owner, type, name, part;

    public Segment(String owner, String type, String name, String part) {
        this.owner = owner;
        this.type  = type;
        this.name  = name;
        this.part  = part;
    }

    // Abstract. Defined in IDataProvider
    public   abstract TableSpace getTableSpace();
    abstract public Supplier<List<IProblematic>> problemsGetter();

    public final String getOwner() {
        return owner;
    }

    public final String getType() {
        return type;
    }

    public final String getName() {
        return name;
    }

    public final String getPart() {
        return part;
    }

    public final List<Extent> getExtents() {
        return new LinkedList<>(extents);
    }

    public final void addExtent(Extent extent) {
        extents.add(extent);
    }

    public final void delExtent(Extent extent) {
        extents.remove(extent);
    }

    public long getSize() {
        return extents.parallelStream().mapToLong(Extent::getSize).sum() * getTableSpace().getBlockSize();
    }

    @Override
    public final String toString() {
        if (part == null) {
            return MessageFormat.format(
                    "Segment [owner={0}, type={1}, name={2}]",
                    owner, type, name
            );
        } else {
            return MessageFormat.format(
                    "Segment [owner={0}, type={1}, name={2}, partition={3}]",
                    owner, type, name, part
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Segment segment = (Segment) o;
        return owner.equals(segment.owner) &&
                type.equals(segment.type) &&
                name.equals(segment.name) &&
                Objects.equals(part, segment.part);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, type, name, part);
    }
}
