package spacemgr.command.objects;

import java.text.MessageFormat;
import java.util.Objects;

public abstract class Extent {

    private final String name;
    private final String owner;
    private final String type;
    private final String part;
    private final long   block;
    private final long   size;

    protected Extent(String owner, String name, String type, String part, long block, long size) {
        this.name  = name;
        this.owner = owner;
        this.type  = type;
        this.part  = part;
        this.block = block;
        this.size  = size;
    }

    public final String getName() {
        return name;
    }

    public final String getOwner() {
        return owner;
    }

    public final String getType() {
        return type;
    }

    public String getPartition() {
        return part;
    }

    public final long getFirstBlock() {
        return block;
    }

    public final long getLastBlock() {
        return block+size-1;
    }

    public final long getSize() {
        return size;
    }

    public abstract Segment getSegment();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Extent extent = (Extent) o;
        return name.equals(extent.name) &&
               owner.equals(extent.owner) &&
               type.equals(extent.type) &&
               block == extent.block &&
               size == extent.size &&
               Objects.equals(part, extent.part);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, owner, type, part, block, size);
    }

    @Override
    public final String toString() {
        if (part == null) {
            return MessageFormat.format(
                    "Extent [#{0}-{1}, type={2}, owner={3}, name={4}]",
                    String.valueOf(block), String.valueOf(block+size-1), type, owner, name
            );
        } else {
            return MessageFormat.format(
                    "Extent [#{0}-{1}, type={2}, owner={3}, name={4}, partition={5}]",
                    String.valueOf(block), String.valueOf(block+size-1), type, owner, name, part
            );
        }
    }
}
