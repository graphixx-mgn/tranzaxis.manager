package codex.command;

import codex.component.button.IButton;

public interface ICommand<T> {
    
    public IButton   getButton();
    public void      setContext(T... context);
    public T[]       getContext();
    public void      execute(T context);
    
}
