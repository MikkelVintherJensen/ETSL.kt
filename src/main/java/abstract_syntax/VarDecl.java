package abstract_syntax;

public final class VarDecl implements Decl {
    public final Type type;
    public final String name;
    public final Expr value;

    public VarDecl(Type type, String name, Expr value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }
}