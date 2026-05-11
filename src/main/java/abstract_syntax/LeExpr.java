package abstract_syntax;

public final class LeExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public LeExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}