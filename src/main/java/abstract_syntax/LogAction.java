package abstract_syntax;

import java.util.List;

public final class LogAction implements Action {
    public final List<String> variables;
    public Action next;  // NOT final - can be modified
    
    public LogAction(List<String> variables, Action next) {
        this.variables = variables;
        this.next = next;
    }
}