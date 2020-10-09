package codex.context;

import codex.log.Level;
import codex.log.LoggingSource;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
@LoggingSource(defaultLevel = Level.Debug)
@IContext.Definition(id = "APP", name = "Application", icon = "/images/general.png")
public class RootContext implements IContext {}
