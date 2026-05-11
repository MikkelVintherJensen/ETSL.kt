package abstract_syntax;

import java.util.List;

public final class Program {
    public final List<Decl> declarations;

    public Program(List<Decl> declarations) {
        this.declarations = declarations;
    }
}