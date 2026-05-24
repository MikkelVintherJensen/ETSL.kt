package interpretation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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
import errors.Diagnostic;
import errors.ErrorCollector;
import errors.ErrorType;

public class Evaluator {
    private final Map<String, Object> envV = new HashMap<>();
    private final Map<String, FuncDecl> envF = new HashMap<>();
    private final Map<String, AgentDecl> envA = new HashMap<>();
    private final Map<String, EventDecl> envE = new HashMap<>();
    private final Map<String, List<String>> envR = new HashMap<>();
    private final Map<String, List<LogEntry>> agentLogs = new HashMap<>();
    private final List<LogEntry> globalLog = new ArrayList<>();
    private final Queue<EventInstance> eventQueue = new LinkedList<>();
    private final ErrorCollector errorCollector;
    
    public Evaluator(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }
    
    public Object evaluate(Program program, List<EventInstance> initialEvents) {
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
                try {
                    Object value = eval(var.value);
                    envV.put(var.name, value);
                } catch (RuntimeException e) {
                    Diagnostic diag = Diagnostic.builder()
                        .type(ErrorType.RUNTIME_TYPE_ERROR)
                        .message(e.getMessage())
                        .build();
                    errorCollector.add(diag);
                }
            }
        }
        
        eventQueue.addAll(initialEvents);
        
        while (!eventQueue.isEmpty()) {
            EventInstance event = eventQueue.poll();
            processEvent(event);
        }
        
        System.out.println("=== Execution Log ===");
        for (LogEntry entry : globalLog) {
            System.out.println(entry);
        }
        
        Object lastResult = null;
        for (Decl decl : program.declarations) {
            if (decl instanceof VarDecl var && envV.containsKey(var.name)) {
                lastResult = envV.get(var.name);
            }
        }
        return lastResult;
    }
    
    private void processEvent(EventInstance event) {
        List<String> listeningAgents = envR.get(event.name);
        if (listeningAgents == null) {
            return;
        }
        
        System.out.println("Processing event: " + event.name + " with values: " + event.values);
        
        for (String agentName : listeningAgents) {
            AgentDecl agent = envA.get(agentName);
            if (agent == null) continue;
            
            Map<String, Object> savedEnv = new HashMap<>(envV);
            
            for (int i = 0; i < agent.params.size() && i < event.values.size(); i++) {
                envV.put(agent.params.get(i).name, event.values.get(i));
            }
            
            System.out.println("  Executing agent: " + agentName);
            
            try {
                executeAction(agent.body, agentName);
            } catch (RuntimeException e) {
                Diagnostic diag = Diagnostic.builder()
                    .type(ErrorType.RUNTIME_TYPE_ERROR)
                    .message("Error in agent '" + agentName + "': " + e.getMessage())
                    .build();
                errorCollector.add(diag);
            }
            
            envV.clear();
            envV.putAll(savedEnv);
        }
    }
    
    private void executeAction(Action action, String currentAgent) {
        if (action == null) return;
        
        if (action instanceof LogAction logAction) {
            Map<String, Object> logData = new LinkedHashMap<>();
            for (String varName : logAction.variables) {
                Object val = envV.get(varName);
                logData.put(varName, val != null ? val : "null");
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
                Diagnostic diag = Diagnostic.builder()
                    .type(ErrorType.RUNTIME_AGENT_NOT_FOUND)
                    .message("Agent '" + callAction.agentName + "' not found")
                    .build();
                errorCollector.add(diag);
                executeAction(callAction.next, currentAgent);
                return;
            }
            
            Map<String, Object> savedEnv = new HashMap<>(envV);
            
            for (int i = 0; i < target.params.size() && i < argValues.size(); i++) {
                envV.put(target.params.get(i).name, argValues.get(i));
            }
            
            System.out.println("    Calling agent: " + callAction.agentName);
            
            executeAction(target.body, target.name);
            
            envV.clear();
            envV.putAll(savedEnv);
            
            executeAction(callAction.next, currentAgent);
            
        } else if (action instanceof SkipAction skipAction) {
            executeAction(skipAction.next, currentAgent);
            
        } else if (action instanceof LetAction letAction) {
            Object value = eval(letAction.value);
            envV.put(letAction.name, value);
            executeAction(letAction.body, currentAgent);
            envV.remove(letAction.name);
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
            Object val = envV.get(varExpr.name);
            if (val == null) {
                FuncDecl func = envF.get(varExpr.name);
                if (func != null && func.params.isEmpty()) {
                    return callFunction(func, List.of());
                }
                throw new RuntimeException("Variable '" + varExpr.name + "' not defined");
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
            if (right == 0) {
                Diagnostic diag = Diagnostic.builder()
                    .type(ErrorType.RUNTIME_DIVISION_BY_ZERO)
                    .message("Division by zero")
                    .build();
                errorCollector.add(diag);
                return 0;
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
            Object savedValue = envV.get(letExpr.name);
            envV.put(letExpr.name, value);
            Object result = eval(letExpr.body);
            if (savedValue != null) {
                envV.put(letExpr.name, savedValue);
            } else {
                envV.remove(letExpr.name);
            }
            return result;
        } else if (expr instanceof CallExpr callExpr) {
            FuncDecl func = envF.get(callExpr.name);
            if (func == null) {
                throw new RuntimeException("Function '" + callExpr.name + "' not defined");
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
        Map<String, Object> savedEnv = new HashMap<>(envV);
        for (int i = 0; i < func.params.size(); i++) {
            envV.put(func.params.get(i).name, args.get(i));
        }
        envV.put(func.name, func);
        Object result = eval(func.body);
        envV.clear();
        envV.putAll(savedEnv);
        envV.put(func.name, func);
        return result;
    }
}