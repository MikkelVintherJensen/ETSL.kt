package abstract_syntax;

public final class EqExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public EqExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}