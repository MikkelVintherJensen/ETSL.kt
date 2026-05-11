package abstract_syntax;

public final class AndExpr implements Expr {
    public final Expr left;
    public final Expr right;

    public AndExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }
}