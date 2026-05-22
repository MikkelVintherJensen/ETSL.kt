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
import abstract_syntax.EmptyExpr;
import abstract_syntax.HeadExpr;
import abstract_syntax.ListExpr;
import abstract_syntax.TailExpr;
import abstract_syntax.RecordExpr;
import abstract_syntax.FieldAccessExpr;

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
    // Report-style global log: AgentId -> AgentLog
    private final Map<String, List<LogEntry>> globalLog = new LinkedHashMap<>();

    // Optional flat chronological timeline of all log entries
    private final List<LogEntry> executionTimeline = new ArrayList<>();

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
                globalLog.put(agent.name, new ArrayList<>());

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

        if (!executionTimeline.isEmpty()) {
            printGlobalLog();
        }

        return lastResult;
    }

    private void printGlobalLog() {
        System.out.println("=== Global Log ===");

        for (Map.Entry<String, List<LogEntry>> agentEntry : globalLog.entrySet()) {
            String agentName = agentEntry.getKey();
            List<LogEntry> agentLog = agentEntry.getValue();

            if (agentLog.isEmpty()) {
                continue;
            }

            System.out.println(agentName + ":");

            for (int i = 0; i < agentLog.size(); i++) {
                LogEntry logEntry = agentLog.get(i);
                System.out.println("  " + i + ": " + logEntry.data);
            }
        }
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
        if (expected.equals(Type.NUM)) {
            return value instanceof Double;
        }

        if (expected.equals(Type.BOOL)) {
            return value instanceof Boolean;
        }

        if (expected.equals(Type.STRING)) {
            return value instanceof String;
        }

        if (expected.isList()) {
            return value instanceof List<?>;
        }

        return false;
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
            executionTimeline.add(entry);
            globalLog.get(currentAgent).add(entry);

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
        } else if (expr instanceof ListExpr listExpr) {
            List<Object> values = new ArrayList<>();

            for (Expr element : listExpr.elements) {
                values.add(eval(element));
            }

            return values;

        } else if (expr instanceof HeadExpr headExpr) {
            Object value = eval(headExpr.list);

            if (!(value instanceof List<?> list)) {
                throw new RuntimeException("head expects a list");
            }

            if (list.isEmpty()) {
                throw new RuntimeException("Cannot take head of empty list");
            }

            return list.get(0);

        } else if (expr instanceof TailExpr tailExpr) {
            Object value = eval(tailExpr.list);

            if (!(value instanceof List<?> list)) {
                throw new RuntimeException("tail expects a list");
            }

            if (list.isEmpty()) {
                throw new RuntimeException("Cannot take tail of empty list");
            }

            return new ArrayList<>(list.subList(1, list.size()));

        } else if (expr instanceof EmptyExpr emptyExpr) {
            Object value = eval(emptyExpr.list);

            if (!(value instanceof List<?> list)) {
                throw new RuntimeException("empty expects a list");
            }

            return list.isEmpty();
        } else if (expr instanceof RecordExpr recordExpr) {
            Map<String, Object> values = new LinkedHashMap<>();

            for (Map.Entry<String, Expr> entry : recordExpr.fields.entrySet()) {
                values.put(entry.getKey(), eval(entry.getValue()));
            }

            return values;

        } else if (expr instanceof FieldAccessExpr fieldAccessExpr) {
            Object recordValue = eval(fieldAccessExpr.record);

            if (!(recordValue instanceof Map<?, ?> record)) {
                throw new RuntimeException("Field access expects a record");
            }

            if (!record.containsKey(fieldAccessExpr.fieldName)) {
                throw new RuntimeException("Record has no field " + fieldAccessExpr.fieldName);
            }

            return record.get(fieldAccessExpr.fieldName);
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