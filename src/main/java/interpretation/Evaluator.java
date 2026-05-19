package interpretation;

import abstract_syntax.*;
import java.util.*;

public class Evaluator {
    private final Map<String, Object> env = new HashMap<>();
    private final Map<String, FuncDecl> functions = new HashMap<>();
    private final Map<String, AgentDecl> agents = new HashMap<>();
    private final Map<String, List<String>> eventToAgents = new HashMap<>();
    private final List<LogEntry> globalLog = new ArrayList<>();
    
    public Object evaluate(Program program, List<EventInstance> initialEvents) {
        // Register all functions and agents
        for (Decl decl : program.declarations) {
            if (decl instanceof FuncDecl func) {
                functions.put(func.name, func);
            } else if (decl instanceof AgentDecl agent) {
                agents.put(agent.name, agent);
                for (String eventName : agent.listensTo) {
                    eventToAgents.computeIfAbsent(eventName, k -> new ArrayList<>())
                                 .add(agent.name);
                }
            } else if (decl instanceof VarDecl var) {
                Object value = eval(var.value);
                env.put(var.name, value);
            }
        }
        
        // Process events
        for (EventInstance event : initialEvents) {
            processEvent(event);
        }
        
        // Print log
        System.out.println("=== Execution Log ===");
        for (LogEntry entry : globalLog) {
            System.out.println(entry);
        }
        
        // Return last variable value (for backward compatibility)
        Object lastResult = null;
        for (Decl decl : program.declarations) {
            if (decl instanceof VarDecl var && env.containsKey(var.name)) {
                lastResult = env.get(var.name);
            }
        }
        return lastResult;
    }
    
    private void processEvent(EventInstance event) {
        List<String> listeningAgents = eventToAgents.get(event.name);
        if (listeningAgents == null) {
            System.out.println("No agents listening to event: " + event.name);
            return;
        }
        
        System.out.println("Processing event: " + event.name + " with values: " + event.values);
        
        for (String agentName : listeningAgents) {
            AgentDecl agent = agents.get(agentName);
            if (agent == null) continue;
            
            // Save current environment
            Map<String, Object> savedEnv = new HashMap<>(env);
            
            // Bind event values to agent parameters
            for (int i = 0; i < agent.params.size() && i < event.values.size(); i++) {
                env.put(agent.params.get(i).name, event.values.get(i));
            }
            
            System.out.println("  Executing agent: " + agentName);
            
            // Execute agent action
            executeAction(agent.body, agentName);
            
            // Restore environment
            env.clear();
            env.putAll(savedEnv);
        }
    }
    
    private void executeAction(Action action, String currentAgent) {
        if (action == null) return;
        
        if (action instanceof LogAction logAction) {
            Map<String, Object> logData = new LinkedHashMap<>();
            for (String varName : logAction.variables) {
                Object val = env.get(varName);
                if (val == null) {
                    logData.put(varName, "null");
                } else {
                    logData.put(varName, val);
                }
            }
            LogEntry entry = new LogEntry(currentAgent, logData);
            globalLog.add(entry);
            System.out.println("    Log: " + entry);
            executeAction(logAction.next, currentAgent);
            
        } else if (action instanceof CallAgentAction callAction) {
            // Evaluate arguments
            List<Object> argValues = new ArrayList<>();
            for (Expr arg : callAction.args) {
                argValues.add(eval(arg));
            }
            
            // Find target agent
            AgentDecl target = agents.get(callAction.agentName);
            if (target == null) {
                System.err.println("Agent " + callAction.agentName + " not found");
                return;
            }
            
            // Save current environment
            Map<String, Object> savedEnv = new HashMap<>(env);
            
            // Bind arguments to target agent parameters
            for (int i = 0; i < target.params.size() && i < argValues.size(); i++) {
                env.put(target.params.get(i).name, argValues.get(i));
            }
            
            System.out.println("    Calling agent: " + callAction.agentName);
            
            // Execute target agent body
            executeAction(target.body, target.name);
            
            // Restore environment
            env.clear();
            env.putAll(savedEnv);
            
            executeAction(callAction.next, currentAgent);
            
        } else if (action instanceof SkipAction skipAction) {
            executeAction(skipAction.next, currentAgent);
            
        } else if (action instanceof LetAction letAction) {
            Object value = eval(letAction.value);
            env.put(letAction.name, value);
            executeAction(letAction.body, currentAgent);
            env.remove(letAction.name);
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
            Object val = env.get(varExpr.name);
            if (val == null) {
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
        env.put(func.name, func);
        Object result = eval(func.body);
        env.clear();
        env.putAll(savedEnv);
        env.put(func.name, func);
        return result;
    }
}