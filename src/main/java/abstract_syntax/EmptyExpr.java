package abstract_syntax;

public final class EmptyExpr implements Expr {
    public final Expr list;

    public EmptyExpr(Expr list) {
        this.list = list;
    }
}