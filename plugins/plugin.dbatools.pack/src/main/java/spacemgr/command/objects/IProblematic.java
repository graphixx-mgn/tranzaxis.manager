package spacemgr.command.objects;

import java.util.Locale;

public interface IProblematic {
    boolean isRelatedSegment(Segment segment);
    String  getDescription(Locale locale);
}
