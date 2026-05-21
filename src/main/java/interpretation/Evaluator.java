package interpretation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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
import abstract_syntax.VarDecl;
import abstract_syntax.VarExpr;
import abstract_syntax.Type;

public class Evaluator {
    // Local/current variable environment: function params, event params, let bindings
    private final Map<String, Object> envV = new HashMap<>();

    // Global declaration environments
    private final Map<String, FuncDecl> envF = new HashMap<>();
    private final Map<String, AgentDecl> envA = new HashMap<>();
    private final Map<String, EventDecl> envE = new HashMap<>();
    private final Map<String, List<String>> envR = new HashMap<>();

    // Global variables are collected first, then evaluated with forward references
    private final Map<String, VarDecl> globalVars = new HashMap<>();
    private final Map<String, Object> globalValues = new HashMap<>();
    private final Set<String> evaluatingGlobals = new HashSet<>();

    // Used to enforce that function bodies cannot read global variables directly
    private boolean insideFunction = false;

    // Logs
    private final Map<String, List<LogEntry>> agentLogs = new HashMap<>();
    private final List<LogEntry> globalLog = new ArrayList<>();

    // Event queue
    private final Queue<EventInstance> eventQueue = new LinkedList<>();

    // Backwards-compatible entry point for expression/declaration-only programs
    public Object evaluate(Program program) {
        return evaluate(program, List.of());
    }

    public Object evaluate(Program program, List<EventInstance> initialEvents) {
        // PHASE 1: collect top-level declarations
        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                envF.put(func.name, func);
            } else if (decl instanceof AgentDecl agent) {
                envA.put(agent.name, agent);
                agentLogs.put(agent.name, new ArrayList<>());

                for (String eventName : agent.listensTo) {
                    envR.computeIfAbsent(eventName, k -> new ArrayList<>()).add(agent.name);
                }
            } else if (decl instanceof EventDecl event) {
                envE.put(event.name, event);
            } else if (decl instanceof VarDecl var) {
                globalVars.put(var.name, var);
            }
        }

        // PHASE 2: evaluate global variables, allowing forward references
        Object lastResult = null;

        for (Decl decl : program.declarations) {
            if (decl instanceof VarDecl var) {
                lastResult = evalGlobal(var.name);
            }
        }

        // PHASE 3: process events
        eventQueue.addAll(initialEvents);

        while (!eventQueue.isEmpty()) {
            EventInstance event = eventQueue.poll();
            processEvent(event);
        }

        if (!globalLog.isEmpty()) {
            System.out.println("=== Execution Log ===");
            for (LogEntry entry : globalLog) {
                System.out.println(entry);
            }
        }

        return lastResult;
    }

    private void validateEventInstance(EventInstance event) {
        EventDecl decl = envE.get(event.name);

        if (decl == null) {
            throw new RuntimeException("Runtime event error: Event " + event.name + " is not declared");
        }

        if (decl.params.size() != event.values.size()) {
            throw new RuntimeException(
                "Runtime event error: Event " + event.name + " expects "
                + decl.params.size() + " values, got " + event.values.size()
            );
        }

        for (int i = 0; i < decl.params.size(); i++) {
            Type expected = decl.params.get(i).type;
            Object value = event.values.get(i);

            if (!matchesType(expected, value)) {
                throw new RuntimeException(
                    "Runtime event error: Event " + event.name
                    + " parameter " + i + " (" + decl.params.get(i).name + ") expects "
                    + expected + ", got " + runtimeTypeName(value)
                );
            }
        }
    }

    private boolean matchesType(Type expected, Object value) {
        return switch (expected) {
            case NUM -> value instanceof Double;
            case BOOL -> value instanceof Boolean;
            case STRING -> value instanceof String;
        };
    }

    private String runtimeTypeName(Object value) {
        if (value instanceof Double) {
            return "NUM";
        }

        if (value instanceof Boolean) {
            return "BOOL";
        }

        if (value instanceof String) {
            return "STRING";
        }

        if (value == null) {
            return "NULL";
        }

        return value.getClass().getSimpleName();
    }

    private Object evalGlobal(String name) {
        if (globalValues.containsKey(name)) {
            return globalValues.get(name);
        }

        VarDecl decl = globalVars.get(name);

        if (decl == null) {
            throw new RuntimeException("Variable " + name + " not defined");
        }

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

    private Object lookupVariable(String name) {
        if (envV.containsKey(name)) {
            return envV.get(name);
        }

        // Function bodies are closed over variables:
        // they may only access parameters and local let-bindings.
        if (insideFunction) {
            throw new RuntimeException("Variable " + name + " not defined");
        }

        if (globalValues.containsKey(name) || globalVars.containsKey(name)) {
            return evalGlobal(name);
        }

        throw new RuntimeException("Variable " + name + " not defined");
    }

    private void processEvent(EventInstance event) {
        validateEventInstance(event);

        List<String> listeningAgents = envR.get(event.name);

        if (listeningAgents == null) {
            System.out.println("No agents listening to event: " + event.name);
            return;
        }

        System.out.println("Processing event: " + event.name + " with values: " + event.values);

        for (String agentName : listeningAgents) {
            AgentDecl agent = envA.get(agentName);

            if (agent == null) {
                continue;
            }

            Map<String, Object> savedEnv = new HashMap<>(envV);

            try {
                for (int i = 0; i < agent.params.size() && i < event.values.size(); i++) {
                    envV.put(agent.params.get(i).name, event.values.get(i));
                }

                System.out.println("  Executing agent: " + agentName);
                executeAction(agent.body, agentName);
            } finally {
                envV.clear();
                envV.putAll(savedEnv);
            }
        }
    }

    private void executeAction(Action action, String currentAgent) {
        if (action == null) {
            return;
        }

        if (action instanceof LogAction logAction) {
            Map<String, Object> logData = new LinkedHashMap<>();

            for (String varName : logAction.variables) {
                Object val;

                try {
                    val = lookupVariable(varName);
                } catch (RuntimeException ex) {
                    val = "undefined";
                }

                logData.put(varName, val);
            }

            LogEntry entry = new LogEntry(currentAgent, logData);
            globalLog.add(entry);
            agentLogs.get(currentAgent).add(entry);

            System.out.println("    Log: " + entry);
            executeAction(logAction.next, currentAgent);

        } else if (action instanceof CallAgentAction callAction) {
            List<Object> argValues = new ArrayList<>();

            for (Expr arg : callAction.args) {
                argValues.add(eval(arg));
            }

            AgentDecl target = envA.get(callAction.agentName);

            if (target == null) {
                System.err.println("Agent " + callAction.agentName + " not found");
                executeAction(callAction.next, currentAgent);
                return;
            }

            Map<String, Object> savedEnv = new HashMap<>(envV);

            try {
                for (int i = 0; i < target.params.size() && i < argValues.size(); i++) {
                    envV.put(target.params.get(i).name, argValues.get(i));
                }

                System.out.println("    Calling agent: " + callAction.agentName);
                executeAction(target.body, target.name);
            } finally {
                envV.clear();
                envV.putAll(savedEnv);
            }

            executeAction(callAction.next, currentAgent);

        } else if (action instanceof SkipAction skipAction) {
            executeAction(skipAction.next, currentAgent);

        } else if (action instanceof LetAction letAction) {
            Object value = eval(letAction.value);

            boolean hadOldValue = envV.containsKey(letAction.name);
            Object savedValue = envV.get(letAction.name);

            try {
                envV.put(letAction.name, value);
                executeAction(letAction.body, currentAgent);
            } finally {
                if (hadOldValue) {
                    envV.put(letAction.name, savedValue);
                } else {
                    envV.remove(letAction.name);
                }
            }

            executeAction(letAction.next, currentAgent);
        }
    }

    private Object eval(Expr expr) {
        if (expr instanceof NumExpr numExpr) {
            return numExpr.value;

        } else if (expr instanceof BoolExpr boolExpr) {
            return boolExpr.value;

        } else if (expr instanceof StringExpr stringExpr) {
            return stringExpr.value;

        } else if (expr instanceof VarExpr varExpr) {
            return lookupVariable(varExpr.name);

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

            if (right == 0.0) {
                throw new RuntimeException("Division by zero");
            }

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

            boolean hadOldValue = envV.containsKey(letExpr.name);
            Object savedValue = envV.get(letExpr.name);

            try {
                envV.put(letExpr.name, value);
                return eval(letExpr.body);
            } finally {
                if (hadOldValue) {
                    envV.put(letExpr.name, savedValue);
                } else {
                    envV.remove(letExpr.name);
                }
            }

        } else if (expr instanceof CallExpr callExpr) {
            FuncDecl func = envF.get(callExpr.name);

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

        Map<String, Object> savedEnv = new HashMap<>(envV);
        boolean savedInsideFunction = insideFunction;

        try {
            envV.clear();
            insideFunction = true;

            for (int i = 0; i < func.params.size(); i++) {
                envV.put(func.params.get(i).name, args.get(i));
            }

            return eval(func.body);
        } finally {
            insideFunction = savedInsideFunction;
            envV.clear();
            envV.putAll(savedEnv);
        }
    }
}