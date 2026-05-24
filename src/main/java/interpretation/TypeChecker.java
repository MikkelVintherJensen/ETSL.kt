package interpretation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import abstract_syntax.Action;
import abstract_syntax.AddExpr;
import abstract_syntax.AgentDecl;
import abstract_syntax.AndExpr;
import abstract_syntax.BoolExpr;
import abstract_syntax.CallAgentAction;
import abstract_syntax.CallExpr;
import abstract_syntax.Decl;
import abstract_syntax.DivExpr;
import abstract_syntax.EqExpr;
import abstract_syntax.EventDecl;
import abstract_syntax.Expr;
import abstract_syntax.FuncDecl;
import abstract_syntax.GeExpr;
import abstract_syntax.GtExpr;
import abstract_syntax.IfExpr;
import abstract_syntax.LeExpr;
import abstract_syntax.LetAction;
import abstract_syntax.LetExpr;
import abstract_syntax.LogAction;
import abstract_syntax.LtExpr;
import abstract_syntax.MulExpr;
import abstract_syntax.NegExpr;
import abstract_syntax.NeqExpr;
import abstract_syntax.NumExpr;
import abstract_syntax.OrExpr;
import abstract_syntax.Program;
import abstract_syntax.SkipAction;
import abstract_syntax.StringExpr;
import abstract_syntax.SubExpr;
import abstract_syntax.Type;
import abstract_syntax.VarDecl;
import abstract_syntax.VarExpr;
import errors.Diagnostic;
import errors.ErrorCollector;
import errors.ErrorType;

public class TypeChecker {
    private final Map<String, Type> varTypes = new HashMap<>();
    private final Map<String, FuncDecl> functions = new HashMap<>();
    private final Map<String, EventDecl> events = new HashMap<>();
    private final Map<String, AgentDecl> agents = new HashMap<>();
    private final Map<String, List<String>> eventToAgents = new HashMap<>();
    private final ErrorCollector errorCollector;
    private int currentLine = 1;
    private int currentCol = 1;

    public TypeChecker(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    public boolean check(Program program) {
        for (Decl decl : program.declarations) {
            if (decl instanceof EventDecl event) {
                if (events.containsKey(event.name)) {
                    error(ErrorType.SEMANTIC_DUPLICATE_DECLARATION, 
                        "Event '" + event.name + "' already declared", currentLine, currentCol);
                }
                events.put(event.name, event);
            }
        }

        for (Decl decl : program.declarations) {
            if (decl instanceof AgentDecl agent) {
                if (agents.containsKey(agent.name)) {
                    error(ErrorType.SEMANTIC_DUPLICATE_DECLARATION, 
                        "Agent '" + agent.name + "' already declared", currentLine, currentCol);
                }
                agents.put(agent.name, agent);
                for (String eventName : agent.listensTo) {
                    eventToAgents.computeIfAbsent(eventName, k -> new ArrayList<>()).add(agent.name);
                }
            }
        }

        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                if (functions.containsKey(func.name)) {
                    error(ErrorType.SEMANTIC_DUPLICATE_DECLARATION, 
                        "Function '" + func.name + "' already declared", currentLine, currentCol);
                }
                functions.put(func.name, func);
            }
        }

        for (Decl decl : program.declarations) {
            checkDecl(decl);
        }

        for (AgentDecl agent : agents.values()) {
            for (String eventName : agent.listensTo) {
                EventDecl event = events.get(eventName);
                if (event == null) {
                    error(ErrorType.SEMANTIC_UNDECLARED_EVENT, 
                        "Agent '" + agent.name + "' listens to undeclared event: " + eventName, 
                        currentLine, currentCol);
                } else if (event.params.size() != agent.params.size()) {
                    error(ErrorType.SEMANTIC_EVENT_PARAMETER_MISMATCH, 
                        "Agent '" + agent.name + "' parameter count (" + agent.params.size() + 
                        ") does not match event '" + eventName + "' (" + event.params.size() + ")",
                        currentLine, currentCol);
                } else {
                    for (int i = 0; i < event.params.size(); i++) {
                        if (event.params.get(i).type != agent.params.get(i).type) {
                            error(ErrorType.SEMANTIC_EVENT_PARAMETER_MISMATCH, 
                                "Agent '" + agent.name + "' parameter " + i + " type mismatch: " +
                                agent.params.get(i).type + " vs event '" + event.params.get(i).type + "'",
                                currentLine, currentCol);
                        }
                    }
                }
            }
        }

        return !errorCollector.hasErrors();
    }

    private void checkDecl(Decl decl) {
        if (decl instanceof VarDecl varDecl) {
            Type exprType = typeOf(varDecl.value);
            if (exprType != varDecl.type) {
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "Variable '" + varDecl.name + "' declared as " + varDecl.type + " but assigned " + exprType,
                    currentLine, currentCol);
            }
            varTypes.put(varDecl.name, varDecl.type);
        } else if (decl instanceof FuncDecl funcDecl) {
            Map<String, Type> savedVarTypes = new HashMap<>(varTypes);
            for (FuncDecl.Param param : funcDecl.params) {
                varTypes.put(param.name, param.type);
            }
            varTypes.put(funcDecl.name, funcDecl.returnType);
            
            Type bodyType = typeOf(funcDecl.body);
            
            if (bodyType != funcDecl.returnType) {
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "Function '" + funcDecl.name + "' declared to return " + funcDecl.returnType + 
                    " but body has type " + bodyType, currentLine, currentCol);
            }
            varTypes.clear();
            varTypes.putAll(savedVarTypes);
            varTypes.put(funcDecl.name, funcDecl.returnType);
        } else if (decl instanceof AgentDecl agentDecl) {
            Map<String, Type> savedVarTypes = new HashMap<>(varTypes);
            for (AgentDecl.Param param : agentDecl.params) {
                varTypes.put(param.name, param.type);
            }
            checkAction(agentDecl.body, varTypes);
            varTypes.clear();
            varTypes.putAll(savedVarTypes);
        }
    }

    private void checkAction(Action action, Map<String, Type> scope) {
        if (action == null) return;
        
        if (action instanceof LogAction logAction) {
            for (String varName : logAction.variables) {
                if (!scope.containsKey(varName) && !varTypes.containsKey(varName)) {
                    error(ErrorType.SEMANTIC_LOG_VARIABLE_NOT_IN_SCOPE, 
                        "Log variable '" + varName + "' not in scope", currentLine, currentCol);
                }
            }
            checkAction(logAction.next, scope);
            
        } else if (action instanceof CallAgentAction callAction) {
            AgentDecl target = agents.get(callAction.agentName);
            if (target == null) {
                error(ErrorType.SEMANTIC_UNDECLARED_AGENT, 
                    "Call to undeclared agent: " + callAction.agentName, currentLine, currentCol);
            } else {
                if (target.params.size() != callAction.args.size()) {
                    error(ErrorType.SEMANTIC_CALL_ARGUMENT_MISMATCH, 
                        "Agent call '" + callAction.agentName + "' expects " + target.params.size() +
                        " arguments, got " + callAction.args.size(), currentLine, currentCol);
                } else {
                    for (int i = 0; i < target.params.size(); i++) {
                        Type argType = typeOf(callAction.args.get(i));
                        if (target.params.get(i).type != argType) {
                            error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                                "Agent call '" + callAction.agentName + "' argument " + i + 
                                " type mismatch: expected " + target.params.get(i).type + 
                                ", got " + argType, currentLine, currentCol);
                        }
                    }
                }
            }
            checkAction(callAction.next, scope);
            
        } else if (action instanceof SkipAction skipAction) {
            checkAction(skipAction.next, scope);
            
        } else if (action instanceof LetAction letAction) {
            Type valType = typeOf(letAction.value);
            if (valType != letAction.type) {
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "Let binding '" + letAction.name + "' declared as " + letAction.type +
                    " but assigned " + valType, currentLine, currentCol);
            }
            Map<String, Type> newScope = new HashMap<>(scope);
            newScope.put(letAction.name, letAction.type);
            checkAction(letAction.body, newScope);
            checkAction(letAction.next, scope);
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
                error(ErrorType.SEMANTIC_UNDECLARED_VARIABLE, 
                    "Variable '" + varExpr.name + "' not declared", currentLine, currentCol);
                return Type.NUM;
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
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "! operator requires bool, got " + t, currentLine, currentCol);
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
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "Cannot compare " + leftType + " with " + rightType, currentLine, currentCol);
            }
            return Type.BOOL;
        } else if (expr instanceof NeqExpr neqExpr) {
            Type leftType = typeOf(neqExpr.left);
            Type rightType = typeOf(neqExpr.right);
            if (leftType != rightType) {
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "Cannot compare " + leftType + " with " + rightType, currentLine, currentCol);
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
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "If condition must be bool, got " + condType, currentLine, currentCol);
            }
            Type thenType = typeOf(ifExpr.thenExpr);
            Type elseType = typeOf(ifExpr.elseExpr);
            if (thenType != elseType) {
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "If branches have different types: " + thenType + " and " + elseType, 
                    currentLine, currentCol);
            }
            return thenType;
        } else if (expr instanceof LetExpr letExpr) {
            Type valType = typeOf(letExpr.value);
            if (valType != letExpr.type) {
                error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                    "Let variable '" + letExpr.name + "' declared as " + letExpr.type + 
                    " but assigned " + valType, currentLine, currentCol);
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
                error(ErrorType.SEMANTIC_UNDECLARED_FUNCTION, 
                    "Function '" + callExpr.name + "' not declared", currentLine, currentCol);
                return Type.NUM;
            }
            if (func.params.size() != callExpr.args.size()) {
                error(ErrorType.SEMANTIC_ARGUMENT_COUNT_MISMATCH, 
                    "Function '" + callExpr.name + "' expects " + func.params.size() + 
                    " arguments, got " + callExpr.args.size(), currentLine, currentCol);
            } else {
                for (int i = 0; i < func.params.size(); i++) {
                    Type paramType = func.params.get(i).type;
                    Type argType = typeOf(callExpr.args.get(i));
                    if (paramType != argType) {
                        error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                            "Function '" + callExpr.name + "' parameter " + i + " expects " + paramType + 
                            ", got " + argType, currentLine, currentCol);
                    }
                }
            }
            return func.returnType;
        }
        return Type.NUM;
    }

    private Type checkArithmetic(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);
        if (leftType != Type.NUM || rightType != Type.NUM) {
            error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                "Operator " + op + " requires num operands, got " + leftType + " and " + rightType,
                currentLine, currentCol);
        }
        return Type.NUM;
    }

    private Type checkRelational(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);
        if (leftType != Type.NUM || rightType != Type.NUM) {
            error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                "Operator " + op + " requires num operands, got " + leftType + " and " + rightType,
                currentLine, currentCol);
        }
        return Type.BOOL;
    }

    private Type checkBoolean(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);
        if (leftType != Type.BOOL || rightType != Type.BOOL) {
            error(ErrorType.SEMANTIC_TYPE_MISMATCH, 
                "Operator " + op + " requires bool operands, got " + leftType + " and " + rightType,
                currentLine, currentCol);
        }
        return Type.BOOL;
    }

    private void error(ErrorType type, String message, int line, int col) {
        Diagnostic diag = Diagnostic.builder()
            .type(type)
            .message(message)
            .location(new Diagnostic.SourceLocation(line, col, 0, ""))
            .build();
        errorCollector.add(diag);
    }
}