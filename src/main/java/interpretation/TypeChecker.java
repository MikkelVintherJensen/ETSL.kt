package interpretation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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
import abstract_syntax.EmptyExpr;
import abstract_syntax.HeadExpr;
import abstract_syntax.ListExpr;
import abstract_syntax.TailExpr;
import abstract_syntax.RecordExpr;
import abstract_syntax.FieldAccessExpr;

public class TypeChecker {
    private final Map<String, Type> varTypes = new HashMap<>();
    private final Map<String, FuncDecl> functions = new HashMap<>();
    private final List<String> errors = new ArrayList<>();

    private final Map<String, EventDecl> events = new HashMap<>();
    private final Map<String, AgentDecl> agents = new HashMap<>();
    private final Map<String, List<String>> eventToAgents = new HashMap<>();

    public boolean check(Program program) {
        // Pass 1: collect all events
        for (Decl decl : program.declarations) {
            if (decl instanceof EventDecl event) {
                if (events.containsKey(event.name)) {
                    error("Event " + event.name + " already declared");
                } else {
                    events.put(event.name, event);
                }
            }
        }

        // Pass 2: collect all agents and build event-to-agent mapping
        for (Decl decl : program.declarations) {
            if (decl instanceof AgentDecl agent) {
                if (agents.containsKey(agent.name)) {
                    error("Agent " + agent.name + " already declared");
                } else {
                    agents.put(agent.name, agent);
                    for (String eventName : agent.listensTo) {
                        eventToAgents.computeIfAbsent(eventName, k -> new ArrayList<>()).add(agent.name);
                    }
                }
            }
        }

        // Pass 3: collect all function declarations
        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                if (functions.containsKey(func.name)) {
                    error("Function " + func.name + " already declared");
                } else {
                    functions.put(func.name, func);
                }
            }
        }

        // Pass 4: collect all global variable declared types.
        // This allows global variable forward references during type checking.
        for (Decl decl : program.declarations) {
            if (decl instanceof VarDecl var) {
                if (varTypes.containsKey(var.name)) {
                    error("Variable " + var.name + " already declared");
                } else {
                    varTypes.put(var.name, var.type);
                }
            }
        }

        // Pass 5: check declarations
        for (Decl decl : program.declarations) {
            checkDecl(decl);
        }

        // Pass 6: validate event-agent connections
        for (AgentDecl agent : agents.values()) {
            for (String eventName : agent.listensTo) {
                EventDecl event = events.get(eventName);

                if (event == null) {
                    error("Agent " + agent.name + " listens to undeclared event: " + eventName);
                } else if (event.params.size() != agent.params.size()) {
                    error(
                        "Agent " + agent.name + " parameter count (" + agent.params.size()
                        + ") does not match event " + eventName + " (" + event.params.size() + ")"
                    );
                } else {
                    for (int i = 0; i < event.params.size(); i++) {
                        if (!sameType(event.params.get(i).type, agent.params.get(i).type)) {
                            error(
                                "Agent " + agent.name + " parameter " + i + " type mismatch: "
                                + agent.params.get(i).type + " vs event " + event.params.get(i).type
                            );
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

            if (!sameType(exprType, varDecl.type)) {
                error("Variable " + varDecl.name + " declared as " + varDecl.type + " but assigned " + exprType);
            }

        } else if (decl instanceof FuncDecl funcDecl) {
            checkFunctionDecl(funcDecl);

        } else if (decl instanceof AgentDecl agentDecl) {
            checkAgentDecl(agentDecl);

        } else if (decl instanceof EventDecl eventDecl) {
            // Events are collected first.
            // Parameter compatibility is checked when validating agents that listen to events.
        }
    }

    private void checkFunctionDecl(FuncDecl funcDecl) {
        // Function bodies are checked in a fresh variable scope.
        // Only parameters and variables introduced by let-expressions inside the body are visible.
        // Global variables are not implicitly available inside functions.
        Map<String, Type> savedVarTypes = new HashMap<>(varTypes);

        varTypes.clear();

        for (FuncDecl.Param param : funcDecl.params) {
            if (varTypes.containsKey(param.name)) {
                error("Duplicate parameter " + param.name + " in function " + funcDecl.name);
            } else {
                varTypes.put(param.name, param.type);
            }
        }

        Type bodyType = typeOf(funcDecl.body);

        if (!sameType(bodyType, funcDecl.returnType)) {
            error(
                "Function " + funcDecl.name + " declared to return " + funcDecl.returnType
                + " but body has type " + bodyType
            );
        }

        varTypes.clear();
        varTypes.putAll(savedVarTypes);
    }

    private void checkAgentDecl(AgentDecl agentDecl) {
        // Agent bodies are checked in a scope containing global variables and agent parameters.
        Map<String, Type> savedVarTypes = new HashMap<>(varTypes);

        for (AgentDecl.Param param : agentDecl.params) {
            if (varTypes.containsKey(param.name)) {
                error("Agent parameter " + param.name + " shadows existing variable in agent " + agentDecl.name);
            }
            varTypes.put(param.name, param.type);
        }

        checkAction(agentDecl.body);

        varTypes.clear();
        varTypes.putAll(savedVarTypes);
    }

    private void checkAction(Action action) {
        if (action == null) {
            return;
        }

        if (action instanceof LogAction logAction) {
            for (String varName : logAction.variables) {
                if (!varTypes.containsKey(varName)) {
                    error("Log variable '" + varName + "' not in scope");
                }
            }

            checkAction(logAction.next);

        } else if (action instanceof CallAgentAction callAction) {
            AgentDecl target = agents.get(callAction.agentName);

            if (target == null) {
                error("Call to undeclared agent: " + callAction.agentName);
            } else if (target.params.size() != callAction.args.size()) {
                error(
                    "Agent call " + callAction.agentName + " expects " + target.params.size()
                    + " arguments, got " + callAction.args.size()
                );
            } else {
                for (int i = 0; i < target.params.size(); i++) {
                    Type expectedType = target.params.get(i).type;
                    Type actualType = typeOf(callAction.args.get(i));

                    if (!sameType(expectedType, actualType)) {
                        error(
                            "Agent call " + callAction.agentName + " argument " + i
                            + " type mismatch: expected " + expectedType + ", got " + actualType
                        );
                    }
                }
            }

            checkAction(callAction.next);

        } else if (action instanceof SkipAction skipAction) {
            checkAction(skipAction.next);

        } else if (action instanceof LetAction letAction) {
            Type valType = typeOf(letAction.value);

            if (!sameType(valType, letAction.type)) {
                error(
                    "Let binding " + letAction.name + " declared as " + letAction.type
                    + " but assigned " + valType
                );
            }

            boolean hadOldType = varTypes.containsKey(letAction.name);
            Type savedType = varTypes.get(letAction.name);

            varTypes.put(letAction.name, letAction.type);
            checkAction(letAction.body);

            if (hadOldType) {
                varTypes.put(letAction.name, savedType);
            } else {
                varTypes.remove(letAction.name);
            }

            checkAction(letAction.next);
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
                return Type.NUM; // Dummy return to continue checking
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

            if (!sameType(t, Type.BOOL)) {
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

            if (!sameType(leftType, rightType)) {
                error("Cannot compare " + leftType + " with " + rightType);
            }

            return Type.BOOL;

        } else if (expr instanceof NeqExpr neqExpr) {
            Type leftType = typeOf(neqExpr.left);
            Type rightType = typeOf(neqExpr.right);

            if (!sameType(leftType, rightType)) {
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

            if (!sameType(condType, Type.BOOL)) {
                error("If condition must be bool, got " + condType);
            }

            Type thenType = typeOf(ifExpr.thenExpr);
            Type elseType = typeOf(ifExpr.elseExpr);

            if (!sameType(thenType, elseType)) {
                error("If branches have different types: " + thenType + " and " + elseType);
            }

            return thenType;

        } else if (expr instanceof LetExpr letExpr) {
            Type valType = typeOf(letExpr.value);

            if (!sameType(valType, letExpr.type)) {
                error("Let variable " + letExpr.name + " declared as " + letExpr.type + " but assigned " + valType);
            }

            boolean hadOldType = varTypes.containsKey(letExpr.name);
            Type savedType = varTypes.get(letExpr.name);

            varTypes.put(letExpr.name, letExpr.type);
            Type bodyType = typeOf(letExpr.body);

            if (hadOldType) {
                varTypes.put(letExpr.name, savedType);
            } else {
                varTypes.remove(letExpr.name);
            }

            return bodyType;

        } else if (expr instanceof CallExpr callExpr) {
            FuncDecl func = functions.get(callExpr.name);

            if (func == null) {
                error("Function " + callExpr.name + " not declared");
                return Type.NUM; // Dummy return to continue checking
            }

            if (func.params.size() != callExpr.args.size()) {
                error(
                    "Function " + callExpr.name + " expects " + func.params.size()
                    + " arguments, got " + callExpr.args.size()
                );
            } else {
                for (int i = 0; i < func.params.size(); i++) {
                    Type paramType = func.params.get(i).type;
                    Type argType = typeOf(callExpr.args.get(i));

                    if (!sameType(paramType, argType)) {
                        error(
                            "Function " + callExpr.name + " parameter " + i
                            + " expects " + paramType + ", got " + argType
                        );
                    }
                }
            }

            return func.returnType;
        } else if (expr instanceof ListExpr listExpr) {
            if (listExpr.elements.isEmpty()) {
                error("Cannot infer type of empty list");
                return Type.list(Type.NUM); // Dummy return
            }

            Type elementType = typeOf(listExpr.elements.get(0));

            for (int i = 1; i < listExpr.elements.size(); i++) {
                Type currentType = typeOf(listExpr.elements.get(i));

                if (!sameType(elementType, currentType)) {
                    error("List elements must have same type, got " + elementType + " and " + currentType);
                }
            }

            return Type.list(elementType);

        } else if (expr instanceof HeadExpr headExpr) {
            Type listType = typeOf(headExpr.list);

            if (!listType.isList()) {
                error("head expects a list, got " + listType);
                return Type.NUM; // Dummy return
            }

            return listType.elementType;

        } else if (expr instanceof TailExpr tailExpr) {
            Type listType = typeOf(tailExpr.list);

            if (!listType.isList()) {
                error("tail expects a list, got " + listType);
                return Type.list(Type.NUM); // Dummy return
            }

            return listType;

        } else if (expr instanceof EmptyExpr emptyExpr) {
            Type listType = typeOf(emptyExpr.list);

            if (!listType.isList()) {
                error("empty expects a list, got " + listType);
            }

            return Type.BOOL;
        } else if (expr instanceof RecordExpr recordExpr) {
            LinkedHashMap<String, Type> fieldTypes = new LinkedHashMap<>();

            for (Map.Entry<String, Expr> entry : recordExpr.fields.entrySet()) {
                if (fieldTypes.containsKey(entry.getKey())) {
                    error("Duplicate record field " + entry.getKey());
                }

                fieldTypes.put(entry.getKey(), typeOf(entry.getValue()));
            }

            return Type.record(fieldTypes);

        } else if (expr instanceof FieldAccessExpr fieldAccessExpr) {
            Type recordType = typeOf(fieldAccessExpr.record);

            if (!recordType.isRecord()) {
                error("Field access expects a record, got " + recordType);
                return Type.NUM; // Dummy return
            }

            Type fieldType = recordType.fieldType(fieldAccessExpr.fieldName);

            if (fieldType == null) {
                error("Record has no field " + fieldAccessExpr.fieldName);
                return Type.NUM; // Dummy return
            }

            return fieldType;
        }

        error("Unknown expression type: " + expr.getClass().getSimpleName());
        return Type.NUM;
    }

    private Type checkArithmetic(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);

        if (!sameType(leftType, Type.NUM) || !sameType(rightType, Type.NUM)) {
            error("Operator " + op + " requires num operands, got " + leftType + " and " + rightType);
        }

        return Type.NUM;
    }

    private Type checkRelational(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);

        if (!sameType(leftType, Type.NUM) || !sameType(rightType, Type.NUM)) {
            error("Operator " + op + " requires num operands, got " + leftType + " and " + rightType);
        }

        return Type.BOOL;
    }

    private Type checkBoolean(Expr left, Expr right, String op) {
        Type leftType = typeOf(left);
        Type rightType = typeOf(right);

        if (!sameType(leftType, Type.BOOL) || !sameType(rightType, Type.BOOL)) {
            error("Operator " + op + " requires bool operands, got " + leftType + " and " + rightType);
        }

        return Type.BOOL;
    }

    private boolean sameType(Type a, Type b) {
        return a != null && a.equals(b);
    }

    private void error(String msg) {
        errors.add(msg);
    }
}