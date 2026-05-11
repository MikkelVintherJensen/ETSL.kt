package abstract_syntax;

public final class IfExpr implements Expr {
    public final Expr cond;
    public final Expr thenExpr;
    public final Expr elseExpr;

    public IfExpr(Expr cond, Expr thenExpr, Expr elseExpr) {
        this.cond = cond;
        this.thenExpr = thenExpr;
        this.elseExpr = elseExpr;
    }
}