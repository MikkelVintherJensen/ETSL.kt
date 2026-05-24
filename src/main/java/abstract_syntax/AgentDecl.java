package abstract_syntax;

import java.util.List;

public final class AgentDecl implements Decl {
    public final String name;
    public final List<Param> params;
    public final List<String> listensTo;
    public final Action body;
    
    public AgentDecl(String name, List<Param> params, List<String> listensTo, Action body) {
        this.name = name;
        this.params = params;
        this.listensTo = listensTo;
        this.body = body;
    }
    
    public static final class Param {
        public final Type type;
        public final String name;
        
        public Param(Type type, String name) {
            this.type = type;
            this.name = name;
        }
    }
}