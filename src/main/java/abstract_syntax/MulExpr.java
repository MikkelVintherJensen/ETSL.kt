package abstract_syntax;

public final class MulExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public MulExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}