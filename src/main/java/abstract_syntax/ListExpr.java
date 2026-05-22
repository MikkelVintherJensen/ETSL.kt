package abstract_syntax;

import java.util.List;

public final class ListExpr implements Expr {
    public final List<Expr> elements;

    public ListExpr(List<Expr> elements) {
        this.elements = elements;
    }
}