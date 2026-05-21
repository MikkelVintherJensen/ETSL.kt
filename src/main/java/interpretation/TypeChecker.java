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

public class TypeChecker {
    private final Map<String, Type> varTypes = new HashMap<>();
    private final Map<String, FuncDecl> functions = new HashMap<>();
    private final List<String> errors = new ArrayList<>();

    private final Map<String, EventDecl> events = new HashMap<>();
    private final Map<String, AgentDecl> agents = new HashMap<>();
    private final Map<String, List<String>> eventToAgents = new HashMap<>();

    public boolean check(Program program) {
        // First pass: collect all events
        for (Decl decl : program.declarations) {
            if (decl instanceof EventDecl event) {
                events.put(event.name, event);
            }
        }

        // Second pass: collect all agents and build event-to-agent mapping
        for (Decl decl : program.declarations) {
            if (decl instanceof AgentDecl agent) {
                agents.put(agent.name, agent);
                for (String eventName : agent.listensTo) {
                    eventToAgents.computeIfAbsent(eventName, k -> new ArrayList<>()).add(agent.name);
                }
            }
        }

        // Third pass: register all function declarations
        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                functions.put(func.name, func);
            }
        }

        // Fourth pass: check each declaration
        for (Decl decl : program.declarations) {
            checkDecl(decl);
        }

        // Fifth pass: validate event-agent connections
        for (AgentDecl agent : agents.values()) {
            for (String eventName : agent.listensTo) {
                EventDecl event = events.get(eventName);
                if (event == null) {
                    error("Agent " + agent.name + " listens to undeclared event: " + eventName);
                } else if (event.params.size() != agent.params.size()) {
                    error("Agent " + agent.name + " parameter count (" + agent.params.size() + 
                          ") does not match event " + eventName + " (" + event.params.size() + ")");
                } else {
                    for (int i = 0; i < event.params.size(); i++) {
                        if (event.params.get(i).type != agent.params.get(i).type) {
                            error("Agent " + agent.name + " parameter " + i + " type mismatch: " +
                                  agent.params.get(i).type + " vs event " + event.params.get(i).type);
                        }
                    }
                }
            }
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
        } else if (decl instanceof AgentDecl agentDecl) {
            // Create local scope with agent parameters
            Map<String, Type> savedVarTypes = new HashMap<>(varTypes);
            for (AgentDecl.Param param : agentDecl.params) {
                varTypes.put(param.name, param.type);
            }
            // Check the action body
            checkAction(agentDecl.body, varTypes);
            // Restore scope
            varTypes.clear();
            varTypes.putAll(savedVarTypes);
        } else if (decl instanceof EventDecl eventDecl) {
            // Events are already collected, nothing to type check
            // Parameter types are validated when checking agents that listen to this event
        }
    }

    private void checkAction(Action action, Map<String, Type> scope) {
        if (action == null) return;
        
        if (action instanceof LogAction logAction) {
            // Validate all logged variables are in scope
            for (String varName : logAction.variables) {
                if (!scope.containsKey(varName) && !varTypes.containsKey(varName)) {
                    error("Log variable '" + varName + "' not in scope");
                }
            }
            checkAction(logAction.next, scope);
            
        } else if (action instanceof CallAgentAction callAction) {
            // Validate target agent exists
            AgentDecl target = agents.get(callAction.agentName);
            if (target == null) {
                error("Call to undeclared agent: " + callAction.agentName);
            } else {
                // Validate argument count matches target agent parameters
                if (target.params.size() != callAction.args.size()) {
                    error("Agent call " + callAction.agentName + " expects " + target.params.size() +
                          " arguments, got " + callAction.args.size());
                } else {
                    // Validate argument types match target agent parameter types
                    for (int i = 0; i < target.params.size(); i++) {
                        Type argType = typeOf(callAction.args.get(i));
                        if (target.params.get(i).type != argType) {
                            error("Agent call " + callAction.agentName + " argument " + i + 
                                  " type mismatch: expected " + target.params.get(i).type + 
                                  ", got " + argType);
                        }
                    }
                }
            }
            checkAction(callAction.next, scope);
            
        } else if (action instanceof SkipAction skipAction) {
            checkAction(skipAction.next, scope);
            
        } else if (action instanceof LetAction letAction) {
            // Validate the expression type matches declared type
            Type valType = typeOf(letAction.value);
            if (valType != letAction.type) {
                error("Let binding " + letAction.name + " declared as " + letAction.type +
                      " but assigned " + valType);
            }
            // Create new scope with the let-bound variable
            Map<String, Type> newScope = new HashMap<>(scope);
            newScope.put(letAction.name, letAction.type);
            // Check the body of the let action
            checkAction(letAction.body, newScope);
            // Check the next action with the original scope (variable not visible after)
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