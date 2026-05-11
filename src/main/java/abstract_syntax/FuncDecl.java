package abstract_syntax;

import java.util.List;

public final class FuncDecl implements Decl {
    public final Type returnType;
    public final String name;
    public final List<Param> params;
    public final Expr body;

    public FuncDecl(Type returnType, String name, List<Param> params, Expr body) {
        this.returnType = returnType;
        this.name = name;        // <-- ADD THIS LINE
        this.params = params;
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