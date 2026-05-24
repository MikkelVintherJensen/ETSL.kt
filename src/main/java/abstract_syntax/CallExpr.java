package abstract_syntax;

import java.util.List;

public final class CallExpr implements Expr {
    public final String name;
    public final List<Expr> args;

    public CallExpr(String name, List<Expr> args) {
        this.name = name;
        this.args = args;
    }
}