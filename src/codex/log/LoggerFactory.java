package codex.log;

public class LoggerFactory implements org.apache.log4j.spi.LoggerFactory {

    LoggerFactory() {}

    @Override
    public Logger makeNewLoggerInstance(String name) {
        return new Logger(name);
    }
}
