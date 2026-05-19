package abstract_syntax;

import java.util.List;

public final class EventDecl implements Decl {  // Add "implements Decl"
    public final String name;
    public final List<Param> params;
    
    public EventDecl(String name, List<Param> params) {
        this.name = name;
        this.params = params;
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