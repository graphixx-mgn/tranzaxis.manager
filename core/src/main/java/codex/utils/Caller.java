package codex.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Caller extends SecurityManager {

    private static final Caller INSTANCE = new Caller();
    public  static Caller getInstance() {
        return INSTANCE;
    }

    public List<Class> getClassStack() {
        return Arrays.stream(getClassContext())
                .filter(aClass -> aClass != Caller.class)
                .collect(Collectors.toList());
    }

}
