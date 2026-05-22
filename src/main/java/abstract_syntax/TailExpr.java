package abstract_syntax;

public final class TailExpr implements Expr {
    public final Expr list;

    public TailExpr(Expr list) {
        this.list = list;
    }
}