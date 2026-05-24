package abstract_syntax;

public final class SubExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public SubExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}