package codex.context;

import codex.log.LoggingSource;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
@LoggingSource
@IContext.Definition(id = "APP", name = "Application", icon = "/images/general.png")
public class RootContext implements IContext {}
