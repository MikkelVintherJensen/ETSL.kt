package abstract_syntax;

public final class LetExpr implements Expr {
    public final Type type;
    public final String name;
    public final Expr value;
    public final Expr body;

    public LetExpr(Type type, String name, Expr value, Expr body) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.body = body;
    }
}