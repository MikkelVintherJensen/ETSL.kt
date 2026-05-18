package abstract_syntax;

public final class LtExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public LtExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}