package abstract_syntax;

public final class GeExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public GeExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}