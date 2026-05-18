package abstract_syntax;

public final class NeqExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public NeqExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}