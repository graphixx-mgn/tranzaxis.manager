package codex.notification;

import codex.service.IContext;

public interface INotificationContext extends IContext {

    NotifyCondition getDefaultCondition();

}
