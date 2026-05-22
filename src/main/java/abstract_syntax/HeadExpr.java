package abstract_syntax;

public final class HeadExpr implements Expr {
    public final Expr list;

    public HeadExpr(Expr list) {
        this.list = list;
    }
}