package abstract_syntax;

import java.util.List;

public final class Program {
    public final List<Decl> declarations;
    
    // For Slice 2 - events are now Decls
    public Program(List<Decl> declarations) {
        this.declarations = declarations;
    }
}