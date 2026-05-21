package interpretation;

import abstract_syntax.*;
import java.util.*;

public class Evaluator {
    private final Map<String, Object> env = new HashMap<>(); // local env: params + lets
    private final Map<String, FuncDecl> functions = new HashMap<>();

    private final Map<String, VarDecl> globalVars = new HashMap<>();      // collected declarations
    private final Map<String, Object> globalValues = new HashMap<>();     // evaluated values
    private final Set<String> evaluatingGlobals = new HashSet<>();        // cycle detection

    public Object evaluate(Program program) {
        // Phase 1: collect all top-level declarations
        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                functions.put(func.name, func);
            } else if (decl instanceof VarDecl var) {
                globalVars.put(var.name, var);
            }
        }

        // Phase 2: evaluate global variables, allowing forward references
        Object lastResult = null;

        for (Decl decl : program.declarations) {
            if (decl instanceof VarDecl var) {
                lastResult = evalGlobal(var.name);
            }
        }

        return lastResult;
    }

    private Object evalGlobal(String name) {
        // Already evaluated
        if (globalValues.containsKey(name)) {
            return globalValues.get(name);
        }

        // Declaration must exist
        VarDecl decl = globalVars.get(name);
        if (decl == null) {
            throw new RuntimeException("Variable " + name + " not defined");
        }

        // Cycle detection
        if (evaluatingGlobals.contains(name)) {
            throw new RuntimeException("Cyclic global variable dependency involving " + name);
        }

        evaluatingGlobals.add(name);

        try {
            Object value = eval(decl.value);
            globalValues.put(name, value);
            return value;
        } finally {
            evaluatingGlobals.remove(name);
        }
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
            // Local bindings from function parameters and let-expressions shadow globals
            if (env.containsKey(varExpr.name)) {
                return env.get(varExpr.name);
            }

            // Global variables may be forward-referenced and are evaluated on demand
            if (globalValues.containsKey(varExpr.name) || globalVars.containsKey(varExpr.name)) {
                return evalGlobal(varExpr.name);
            }

            throw new RuntimeException("Variable " + varExpr.name + " not defined");
        } else if (expr instanceof AddExpr addExpr) {
            double left = (Double) eval(addExpr.left);
            double right = (Double) eval(addExpr.right);
            return left + right;
        } else if (expr instanceof SubExpr subExpr) {
            double left = (Double) eval(subExpr.left);
            double right = (Double) eval(subExpr.right);
            return left - right;
        } else if (expr instanceof MulExpr mulExpr) {
            double left = (Double) eval(mulExpr.left);
            double right = (Double) eval(mulExpr.right);
            return left * right;
        } else if (expr instanceof DivExpr divExpr) {
            double left = (Double) eval(divExpr.left);
            double right = (Double) eval(divExpr.right);
            if (right == 0.0) throw new RuntimeException("Division by zero");
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
            double left = (Double) eval(ltExpr.left);
            double right = (Double) eval(ltExpr.right);
            return left < right;
        } else if (expr instanceof LeExpr leExpr) {
            double left = (Double) eval(leExpr.left);
            double right = (Double) eval(leExpr.right);
            return left <= right;
        } else if (expr instanceof GtExpr gtExpr) {
            double left = (Double) eval(gtExpr.left);
            double right = (Double) eval(gtExpr.right);
            return left > right;
        } else if (expr instanceof GeExpr geExpr) {
            double left = (Double) eval(geExpr.left);
            double right = (Double) eval(geExpr.right);
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
        if (args.size() != func.params.size()) {
            throw new RuntimeException(
                "Function " + func.name + " expects " + func.params.size()
                + " arguments, got " + args.size()
            );
        }

        Map<String, Object> savedEnv = new HashMap<>(env);

        try {
            env.clear();

            for (int i = 0; i < func.params.size(); i++) {
                env.put(func.params.get(i).name, args.get(i));
            }

            return eval(func.body);
        } finally {
            env.clear();
            env.putAll(savedEnv);
        }
    }
}