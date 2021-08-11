package manager.processor.database;

import codex.utils.Caller;
import manager.nodes.Database;
import manager.processor.Context;
import java.text.MessageFormat;
import java.util.function.Predicate;

public class DatabaseContext extends Context {

    private final Database database;

    public DatabaseContext(Database database) {
        this(database, Caller.getInstance().getClassStack().stream()
                .map(aClass -> aClass.isMemberClass() ? aClass.getEnclosingClass() : aClass)
                .filter(((Predicate<Class>) Class::isAnonymousClass).negate())
                .filter(((Predicate<Class>) Context.class::isAssignableFrom).negate())
                .findFirst().get()
        );
    }

    public DatabaseContext(Database database, Class<?> provision) {
        super(provision);
        this.database = database;
    }

    public final Database getDatabase() {
        return database;
    }

    @Override
    public String toString() {
        return MessageFormat.format("[DB={0}, Class={1}]", getDatabase(), getProvisionClass());
    }
}