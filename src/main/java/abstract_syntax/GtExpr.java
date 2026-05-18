package abstract_syntax;

public final class GtExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public GtExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}