package abstract_syntax;

public final class NegExpr implements Expr {
    public final Expr expr;

    public NegExpr(Expr expr) {
        this.expr = expr;
    }
}