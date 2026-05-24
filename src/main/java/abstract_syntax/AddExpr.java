package abstract_syntax;

public final class AddExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public AddExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}