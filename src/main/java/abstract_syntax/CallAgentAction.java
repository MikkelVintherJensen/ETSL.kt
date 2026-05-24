package abstract_syntax;

import java.util.List;

public final class CallAgentAction implements Action {
    public final String agentName;
    public final List<Expr> args;
    public Action next;
    
    public CallAgentAction(String agentName, List<Expr> args, Action next) {
        this.agentName = agentName;
        this.args = args;
        this.next = next;
    }
}