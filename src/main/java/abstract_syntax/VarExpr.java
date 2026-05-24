package abstract_syntax;

public final class VarExpr implements Expr {
    public final String name;

    public VarExpr(String name) {
        this.name = name;
    }
}