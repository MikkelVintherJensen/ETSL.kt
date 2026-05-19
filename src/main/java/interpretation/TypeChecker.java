package interpretation;

import abstract_syntax.*;
import java.util.*;

public class TypeChecker {
    private final Map<String, Type> varTypes = new HashMap<>();
    private final Map<String, FuncDecl> functions = new HashMap<>();
    private final List<String> errors = new ArrayList<>();


    private final Map<String, EventDecl> events = new HashMap<>();
    private final Map<String, AgentDecl> agents = new HashMap<>();

    public boolean check(Program program) {
        // Register all function declarations
        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                functions.put(func.name, func);
            }
        }
        // Check each declaration
        for (Decl decl : program.declarations) {
            checkDecl(decl);
        }
        for (String error : errors) {
            System.err.println("TYPE ERROR: " + error);
        }
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }

    private void checkDecl(Decl decl) {
        if (decl instanceof VarDecl varDecl) {
            Type exprType = typeOf(varDecl.value);
            if (exprType != varDecl.type) {
                error("Variable " + varDecl.name + " declared as " + varDecl.type + " but assigned " + exprType);
            }
            varTypes.put(varDecl.name, varDecl.type);
        } else if (decl instanceof FuncDecl funcDecl) {
            // Create local scope with parameters
            Map<String, Type> savedVarTypes = new HashMap<>(varTypes);
            for (FuncDecl.Param param : funcDecl.params) {
                varTypes.put(param.name, param.type);
            }
            // Add function to scope for recursion
            varTypes.put(funcDecl.name, funcDecl.returnType);
            
            Type bodyType = typeOf(funcDecl.body);
            
            if (bodyType != funcDecl.returnType) {
                error("Function " + funcDecl.name + " declared to return " + funcDecl.returnType + " but body has type " + bodyType);
            }
            // Restore scope
            varTypes.clear();
            varTypes.putAll(savedVarTypes);
            varTypes.put(funcDecl.name, funcDecl.returnType);
        }
    }

    private Type typeOf(Expr expr) {
        if (expr instanceof NumExpr) {
            return Type.NUM;
        } else if (expr instanceof BoolExpr) {
            return Type.BOOL;
        } else if (expr instanceof StringExpr) {
            return Type.STRING;
        } else if (expr instanceof VarExpr varExpr) {
            Type t = varTypes.get(varExpr.name);
            if (t == null) {
                error("Variable " + varExpr.name + " not declared");
                return Type.NUM; // Dummy return
            }
            return t;
        } else if (expr instanceof AddExpr addExpr) {
            return checkArithmetic(addExpr.left, addExpr.right, "+");
        } else if (expr instanceof SubExpr subExpr) {
            return checkArithmetic(subExpr.left, subExpr.right, "-");
        } else if (expr instanceof MulExpr mulExpr) {
            return checkArithmetic(mulExpr.left, mulExpr.right, "*");
        } else if (expr instanceof DivExpr divExpr) {
            return checkArithmetic(divExpr.left, divExpr.right, "/");
        } else if (expr instanceof NegExpr negExpr) {
            Type t = typeOf(negExpr.expr);
            if (t != Type.BOOL) {
                error("! operator requires bool, got " + t);
            }
            return Type.BOOL;
        } else if (expr instanceof AndExpr andExpr) {
            return checkBoolean(andExpr.left, andExpr.right, "&&");
        } else if (expr instanceof OrExpr orExpr) {
            return checkBoolean(orExpr.left, orExpr.right, "||");
        } else if (expr instanceof EqExpr eqExpr) {
            Type leftType = typeOf(eqExpr.left);
            Type rightType = typeOf(eqExpr.right);
            if (leftType != rightType) {
                error("Cannot compare " + leftType + " with " + rightType);
            }
            return Type.BOOL;
        } else if (expr instanceof NeqExpr neqExpr) {
            Type leftType = typeOf(neqExpr.left);
            Type rightType = typeOf(neqExpr.right);
            if (leftType != rightType) {
                error("Cannot compare " + leftType + " with " + rightType);
            }
            return Type.BOOL;
        } else if (expr instanceof LtExpr ltExpr) {
            return checkRelational(ltExpr.left, ltExpr.right, "<");
        } else if (expr instanceof LeExpr leExpr) {
            return checkRelational(leExpr.left, leExpr.right, "<=");
        } else if (expr instanceof GtExpr gtExpr) {
            return checkRelational(gtExpr.left, gtExpr.right, ">");
        } else if (expr instanceof GeExpr geExpr) {
            return checkRelational(geExpr.left, geExpr.right, ">=");
        } else if (expr instanceof IfExpr ifExpr) {
            Type condType = typeOf(ifExpr.cond);
            if (condType != Type.BOOL) {
                error("If condition must be bool, got " + condType);
            }
            Type thenType = typeOf(ifExpr.thenExpr);
            Type elseType = typeOf(ifExpr.elseExpr);
            if (thenType != elseType) {
                error("If branches have different types: " + thenType + " and " + elseType);
            }
            return thenType;
        } else if (expr instanceof LetExpr letExpr) {
            Type valType = typeOf(letExpr.value);
            if (valType != letExpr.type) {
                error("Let variable " + letExpr.name + " declared as " + letExpr.type + " but assigned " + valType);
            }
            Type savedType = varTypes.get(letExpr.name);
            varTypes.put(letExpr.name, letExpr.type);
            Type bodyType = typeOf(letExpr.body);
            if (savedType != null) {
                varTypes.put(letExpr.name, savedType);
            } else {
                varTypes.remove(letExpr.name);
            }
            return bodyType;
        } else if (expr instanceof CallExpr callExpr) {
            FuncDecl func = functions.get(callExpr.name);
            if (func == null) {
                error("Function " + callExpr.name + " not declared");
                return Type.NUM; // Dummy
            }
            if (func.params.size() != callExpr.args.size()) {
                error("Function " + callExpr.name + " expects " + func.params.size() + " arguments, got " + callExpr.args.size());
            } else {
                for (int i = 0; i < func.params.size(); i++) {
                    Type paramType = func.params.get(i).type;
                    Type argType = typeOf(callExpr.args.get(i));
                    if (paramType != argType) {
                        error("Function " + callExpr.name + " parameter " + i + " expects " + paramType + ", got " + argType);
                    }
                }
            }
            return func.returnType;
        }
        error("Unknown expression type: " + expr.getClass().getSimpleName());
        return Type.NUM;
    }

    private Type checkArithmetic(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);
        if (leftType != Type.NUM || rightType != Type.NUM) {
            error("Operator " + op + " requires num operands, got " + leftType + " and " + rightType);
        }
        return Type.NUM;
    }

    private Type checkRelational(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);
        if (leftType != Type.NUM || rightType != Type.NUM) {
            error("Operator " + op + " requires num operands, got " + leftType + " and " + rightType);
        }
        return Type.BOOL;
    }

    private Type checkBoolean(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);
        if (leftType != Type.BOOL || rightType != Type.BOOL) {
            error("Operator " + op + " requires bool operands, got " + leftType + " and " + rightType);
        }
        return Type.BOOL;
    }

    private void error(String msg) {
        errors.add(msg);
    }
}