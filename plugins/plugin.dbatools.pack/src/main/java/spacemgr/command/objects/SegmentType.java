package spacemgr.command.objects;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.*;

public enum SegmentType implements Iconified {

    TABLE("TABLE", ImageUtils.getByPath("/images/table.png")),
    TABLE_PARTITION("TABLE PARTITION", ImageUtils.getByPath("/images/partition_tbl.png")),
    TABLE_SUBPARTITION("TABLE SUBPARTITION", ImageUtils.getByPath("/images/partition_tbl.png")),

    LOB("LOBSEGMENT", ImageUtils.getByPath("/images/lob.png")),
    LOB_PARTITION("LOB PARTITION", ImageUtils.getByPath("/images/partition_lob.png")),
    LOB_SUBPARTITION("LOB SUBPARTITION", ImageUtils.getByPath("/images/partition_lob.png")),

    INDEX("INDEX", ImageUtils.getByPath("/images/index.png")),
    INDEX_PARTITION("INDEX PARTITION", ImageUtils.getByPath("/images/partition_idx.png")),
    INDEX_SUBPARTITION("INDEX SUBPARTITION", ImageUtils.getByPath("/images/partition_idx.png")),
    LOBINDEX("LOBINDEX", ImageUtils.getByPath("/images/lob_idx.png"))
    ;

    private final String    type;
    private final ImageIcon icon;

    SegmentType(String name, ImageIcon icon) {
        this.type = name;
        this.icon = icon;
    }

    public static SegmentType byType(String name) {
        for (SegmentType type : SegmentType.values()) {
            if (type.type.equals(name)) {
                return type;
            }
        }
        return null;
    }

    public String getType() {
        return type;
    }

    @Override
    public final ImageIcon getIcon() {
        return icon;
    }
}
