package interpretation;

import abstract_syntax.*;
import java.util.*;

public class Evaluator {
    private final Map<String, Object> env = new HashMap<>();
    private final Map<String, FuncDecl> functions = new HashMap<>();

    public Object evaluate(Program program) {
        // Register all functions
        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                functions.put(func.name, func);
            }
        }
        // Evaluate declarations in order
        Object lastResult = null;
        for (Decl decl : program.declarations) {
            lastResult = evaluateDecl(decl);
        }
        return lastResult;
    }

    private Object evaluateDecl(Decl decl) {
        if (decl instanceof VarDecl varDecl) {
            Object value = eval(varDecl.value);
            env.put(varDecl.name, value);
            return value;
        } else if (decl instanceof FuncDecl funcDecl) {
            // Functions are already registered, nothing to evaluate at declaration
            return null;
        }
        return null;
    }

    private Object eval(Expr expr) {
        if (expr instanceof NumExpr numExpr) {
            return numExpr.value;
        } else if (expr instanceof BoolExpr boolExpr) {
            return boolExpr.value;
        } else if (expr instanceof StringExpr stringExpr) {
            return stringExpr.value;
        } else if (expr instanceof VarExpr varExpr) {
            Object val = env.get(varExpr.name);
            if (val == null) {
                // Check if it's a function name (zero-arg call)
                FuncDecl func = functions.get(varExpr.name);
                if (func != null && func.params.isEmpty()) {
                    return callFunction(func, List.of());
                }
                throw new RuntimeException("Variable " + varExpr.name + " not defined");
            }
            return val;
        } else if (expr instanceof AddExpr addExpr) {
            int left = (Integer) eval(addExpr.left);
            int right = (Integer) eval(addExpr.right);
            return left + right;
        } else if (expr instanceof SubExpr subExpr) {
            int left = (Integer) eval(subExpr.left);
            int right = (Integer) eval(subExpr.right);
            return left - right;
        } else if (expr instanceof MulExpr mulExpr) {
            int left = (Integer) eval(mulExpr.left);
            int right = (Integer) eval(mulExpr.right);
            return left * right;
        } else if (expr instanceof DivExpr divExpr) {
            int left = (Integer) eval(divExpr.left);
            int right = (Integer) eval(divExpr.right);
            if (right == 0) throw new RuntimeException("Division by zero");
            return left / right;
        } else if (expr instanceof NegExpr negExpr) {
            boolean val = (Boolean) eval(negExpr.expr);
            return !val;
        } else if (expr instanceof AndExpr andExpr) {
            boolean left = (Boolean) eval(andExpr.left);
            boolean right = (Boolean) eval(andExpr.right);
            return left && right;
        } else if (expr instanceof OrExpr orExpr) {
            boolean left = (Boolean) eval(orExpr.left);
            boolean right = (Boolean) eval(orExpr.right);
            return left || right;
        } else if (expr instanceof EqExpr eqExpr) {
            Object left = eval(eqExpr.left);
            Object right = eval(eqExpr.right);
            return left.equals(right);
        } else if (expr instanceof NeqExpr neqExpr) {
            Object left = eval(neqExpr.left);
            Object right = eval(neqExpr.right);
            return !left.equals(right);
        } else if (expr instanceof LtExpr ltExpr) {
            int left = (Integer) eval(ltExpr.left);
            int right = (Integer) eval(ltExpr.right);
            return left < right;
        } else if (expr instanceof LeExpr leExpr) {
            int left = (Integer) eval(leExpr.left);
            int right = (Integer) eval(leExpr.right);
            return left <= right;
        } else if (expr instanceof GtExpr gtExpr) {
            int left = (Integer) eval(gtExpr.left);
            int right = (Integer) eval(gtExpr.right);
            return left > right;
        } else if (expr instanceof GeExpr geExpr) {
            int left = (Integer) eval(geExpr.left);
            int right = (Integer) eval(geExpr.right);
            return left >= right;
        } else if (expr instanceof IfExpr ifExpr) {
            boolean cond = (Boolean) eval(ifExpr.cond);
            return cond ? eval(ifExpr.thenExpr) : eval(ifExpr.elseExpr);
        } else if (expr instanceof LetExpr letExpr) {
            Object value = eval(letExpr.value);
            Object savedValue = env.get(letExpr.name);
            env.put(letExpr.name, value);
            Object result = eval(letExpr.body);
            if (savedValue != null) {
                env.put(letExpr.name, savedValue);
            } else {
                env.remove(letExpr.name);
            }
            return result;
        } else if (expr instanceof CallExpr callExpr) {
            FuncDecl func = functions.get(callExpr.name);
            if (func == null) {
                throw new RuntimeException("Function " + callExpr.name + " not defined");
            }
            List<Object> args = new ArrayList<>();
            for (Expr arg : callExpr.args) {
                args.add(eval(arg));
            }
            return callFunction(func, args);
        }
        throw new RuntimeException("Unknown expression: " + expr.getClass().getSimpleName());
    }

    private Object callFunction(FuncDecl func, List<Object> args) {
        Map<String, Object> savedEnv = new HashMap<>(env);
        for (int i = 0; i < func.params.size(); i++) {
            env.put(func.params.get(i).name, args.get(i));
        }
        env.put(func.name, func); // For recursion
        Object result = eval(func.body);
        env.clear();
        env.putAll(savedEnv);
        env.put(func.name, func);
        return result;
    }
}