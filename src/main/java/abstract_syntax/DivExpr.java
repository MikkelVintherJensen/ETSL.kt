package abstract_syntax;

public final class DivExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public DivExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}