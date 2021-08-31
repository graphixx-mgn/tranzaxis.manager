package manager.processor.database;

import codex.task.TaskOutput;
import codex.utils.Language;
import manager.processor.AbstractProcessor;
import org.bridj.util.Pair;
import java.sql.SQLException;
import java.text.MessageFormat;

public abstract class DatabaseProcessor<C extends DatabaseContext> extends AbstractProcessor<C> {

    public final static String TITLE_TAG_SUFFIX = ".title";
    public final static String QUERY_TAG_SUFFIX = ".query";

    protected DatabaseProcessor(C context) {
        super(context);
    }

    protected abstract String executeSql(String query, Object...parameters) throws SQLException;

    protected String generateTitle(String tag, Object...parameters) {
        return MessageFormat.format(Language.get(getContext().getProvisionClass(), tag.concat(TITLE_TAG_SUFFIX)), parameters);
    }

    protected String generateQuery(String tag, Object...parameters) {
        return MessageFormat.format(Language.get(getContext().getProvisionClass(), tag.concat(QUERY_TAG_SUFFIX)), parameters);
    }

    public TaskOutput.ExecPhase<Void> prepare(String tag, Object... parameters) {
        return new TaskOutput.ExecPhase<Void>(generateTitle(tag, parameters)) {
            @Override
            protected Pair<String, Void> execute() throws Exception {
                return new Pair<>(DatabaseProcessor.this.executeSql(generateQuery(tag, parameters), parameters), null);
            }
        };
    }
}
