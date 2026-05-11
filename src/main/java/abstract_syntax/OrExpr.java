package abstract_syntax;

public final class OrExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public OrExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}