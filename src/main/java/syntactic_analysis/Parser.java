package syntactic_analysis;

public class Parser {
	public static final int _EOF = 0;
	public static final int _id = 1;
	public static final int _num = 2;
	public static final int _string = 3;
	public static final int maxT = 39;

	static final boolean _T = true;
	static final boolean _x = false;
	static final int minErrDist = 2;

	public Token t;    // last recognized token
	public Token la;   // lookahead token
	int errDist = minErrDist;
	
	public Scanner scanner;
	public Errors errors;

	public abstract_syntax.Program program;

public abstract_syntax.Program getProgram() {
    return program;
}



	public Parser(Scanner scanner) {
		this.scanner = scanner;
		errors = new Errors();
	}

	void SynErr (int n) {
		if (errDist >= minErrDist) errors.SynErr(la.line, la.col, n);
		errDist = 0;
	}

	public void SemErr (String msg) {
		if (errDist >= minErrDist) errors.SemErr(t.line, t.col, msg);
		errDist = 0;
	}
	
	void Get () {
		for (;;) {
			t = la;
			la = scanner.Scan();
			if (la.kind <= maxT) {
				++errDist;
				break;
			}

			la = t;
		}
	}
	
	void Expect (int n) {
		if (la.kind==n) Get(); else { SynErr(n); }
	}
	
	boolean StartOf (int s) {
		return set[s][la.kind];
	}
	
	void ExpectWeak (int n, int follow) {
		if (la.kind == n) Get();
		else {
			SynErr(n);
			while (!StartOf(follow)) Get();
		}
	}
	
	boolean WeakSeparator (int n, int syFol, int repFol) {
		int kind = la.kind;
		if (kind == n) { Get(); return true; }
		else if (StartOf(repFol)) return false;
		else {
			SynErr(n);
			while (!(set[syFol][kind] || set[repFol][kind] || set[0][kind])) {
				Get();
				kind = la.kind;
			}
			return StartOf(syFol);
		}
	}
	
	void ETSL() {
		program = Decls();
	}

	abstract_syntax.Program  Decls() {
		abstract_syntax.Program  prog;
		prog = null; java.util.List<abstract_syntax.Decl> list = new java.util.ArrayList<>(); abstract_syntax.Decl d; 
		while (StartOf(1)) {
			d = Decl();
			list.add(d); 
		}
		prog = new abstract_syntax.Program(list); 
		return prog;
	}

	abstract_syntax.Decl  Decl() {
		abstract_syntax.Decl  node;
		node = null; abstract_syntax.Type type = null; 
		if (la.kind == 18 || la.kind == 19 || la.kind == 20) {
			type = Type();
			if (la.kind == 4) {
				Get();
				String name = ""; java.util.List<abstract_syntax.FuncDecl.Param> params = null; abstract_syntax.Expr body = null; 
				Expect(1);
				name = t.val; 
				Expect(5);
				if (la.kind == 18 || la.kind == 19 || la.kind == 20) {
					params  = ParamList();
				}
				Expect(6);
				Expect(7);
				body = Exp();
				Expect(8);
				node = new abstract_syntax.FuncDecl(type, name, params != null ? params : new java.util.ArrayList<>(), body); 
			} else if (la.kind == 1) {
				String name = ""; abstract_syntax.Expr value = null; 
				Get();
				name = t.val; 
				Expect(7);
				value = Exp();
				Expect(8);
				node = new abstract_syntax.VarDecl(type, name, value); 
			} else SynErr(40);
		} else if (la.kind == 9) {
			Get();
			String name = ""; java.util.List<abstract_syntax.EventDecl.Param> params = null; 
			Expect(1);
			name = t.val; 
			Expect(5);
			if (la.kind == 18 || la.kind == 19 || la.kind == 20) {
				params = EventParamList();
			}
			Expect(6);
			Expect(8);
			node = new abstract_syntax.EventDecl(name, params != null ? params : new java.util.ArrayList<>()); 
		} else if (la.kind == 10) {
			Get();
			String name = ""; java.util.List<abstract_syntax.AgentDecl.Param> params = null; java.util.List<String> events = null; abstract_syntax.Action act = null; 
			Expect(1);
			name = t.val; 
			Expect(5);
			if (la.kind == 18 || la.kind == 19 || la.kind == 20) {
				params = AgentParamList();
			}
			Expect(6);
			Expect(11);
			Expect(5);
			if (la.kind == 1) {
				events = IdList();
			}
			Expect(6);
			Expect(7);
			act = Action();
			Expect(8);
			node = new abstract_syntax.AgentDecl(name, params != null ? params : new java.util.ArrayList<>(), events != null ? events : new java.util.ArrayList<>(), act); 
		} else SynErr(41);
		return node;
	}

	abstract_syntax.Type  Type() {
		abstract_syntax.Type  t;
		t = null; 
		if (la.kind == 18) {
			Get();
			t = abstract_syntax.Type.NUM; 
		} else if (la.kind == 19) {
			Get();
			t = abstract_syntax.Type.BOOL; 
		} else if (la.kind == 20) {
			Get();
			t = abstract_syntax.Type.STRING; 
		} else SynErr(42);
		return t;
	}

	java.util.List<abstract_syntax.FuncDecl.Param>  ParamList() {
		java.util.List<abstract_syntax.FuncDecl.Param>  list;
		list = new java.util.ArrayList<>(); abstract_syntax.FuncDecl.Param p; 
		p = Param();
		list.add(p); 
		while (la.kind == 12) {
			Get();
			p = Param();
			list.add(p); 
		}
		return list;
	}

	abstract_syntax.Expr  Exp() {
		abstract_syntax.Expr  node;
		node = ExpOr();
		return node;
	}

	java.util.List<abstract_syntax.EventDecl.Param>  EventParamList() {
		java.util.List<abstract_syntax.EventDecl.Param>  list;
		list = new java.util.ArrayList<>(); abstract_syntax.EventDecl.Param p; 
		p = EventParam();
		list.add(p); 
		while (la.kind == 12) {
			Get();
			p = EventParam();
			list.add(p); 
		}
		return list;
	}

	java.util.List<abstract_syntax.AgentDecl.Param>  AgentParamList() {
		java.util.List<abstract_syntax.AgentDecl.Param>  list;
		list = new java.util.ArrayList<>(); abstract_syntax.AgentDecl.Param p; 
		p = AgentParam();
		list.add(p); 
		while (la.kind == 12) {
			Get();
			p = AgentParam();
			list.add(p); 
		}
		return list;
	}

	java.util.List<String>  IdList() {
		java.util.List<String>  list;
		list = new java.util.ArrayList<>(); 
		Expect(1);
		list.add(t.val); 
		while (la.kind == 12) {
			Get();
			Expect(1);
			list.add(t.val); 
		}
		return list;
	}

	abstract_syntax.Action  Action() {
		abstract_syntax.Action  act;
		act = null; java.util.List<String> vars = null; String name = ""; java.util.List<abstract_syntax.Expr> args = null; abstract_syntax.Type type = null; abstract_syntax.Expr value = null; abstract_syntax.Action body = null; 
		if (la.kind == 13) {
			Get();
			Expect(5);
			if (la.kind == 1) {
				vars = IdList();
			}
			Expect(6);
			act = new abstract_syntax.LogAction(vars != null ? vars : new java.util.ArrayList<>(), null); 
		} else if (la.kind == 14) {
			Get();
			Expect(1);
			name = t.val; 
			Expect(5);
			if (StartOf(2)) {
				args = ExpList();
			}
			Expect(6);
			act = new abstract_syntax.CallAgentAction(name, args != null ? args : new java.util.ArrayList<>(), null); 
		} else if (la.kind == 15) {
			Get();
			act = new abstract_syntax.SkipAction(null); 
		} else if (la.kind == 16) {
			Get();
			type = Type();
			Expect(1);
			Expect(7);
			value = Exp();
			Expect(17);
			body = Action();
			act = new abstract_syntax.LetAction(type, t.val, value, body, null); 
		} else SynErr(43);
		return act;
	}

	abstract_syntax.FuncDecl.Param  Param() {
		abstract_syntax.FuncDecl.Param  p;
		p = null; abstract_syntax.Type type = null; String name = ""; 
		type = Type();
		Expect(1);
		name = t.val; p = new abstract_syntax.FuncDecl.Param(type, name); 
		return p;
	}

	abstract_syntax.EventDecl.Param  EventParam() {
		abstract_syntax.EventDecl.Param  p;
		p = null; abstract_syntax.Type type = null; String name = ""; 
		type = Type();
		Expect(1);
		name = t.val; p = new abstract_syntax.EventDecl.Param(type, name); 
		return p;
	}

	abstract_syntax.AgentDecl.Param  AgentParam() {
		abstract_syntax.AgentDecl.Param  p;
		p = null; abstract_syntax.Type type = null; String name = ""; 
		type = Type();
		Expect(1);
		name = t.val; p = new abstract_syntax.AgentDecl.Param(type, name); 
		return p;
	}

	java.util.List<abstract_syntax.Expr>  ExpList() {
		java.util.List<abstract_syntax.Expr>  list;
		list = new java.util.ArrayList<>(); abstract_syntax.Expr e; 
		e = Exp();
		list.add(e); 
		while (la.kind == 12) {
			Get();
			e = Exp();
			list.add(e); 
		}
		return list;
	}

	abstract_syntax.Expr  ExpOr() {
		abstract_syntax.Expr  node;
		abstract_syntax.Expr right; 
		node = ExpAnd();
		while (la.kind == 21) {
			Get();
			right = ExpAnd();
			node = new abstract_syntax.OrExpr(node, right); 
		}
		return node;
	}

	abstract_syntax.Expr  ExpAnd() {
		abstract_syntax.Expr  node;
		abstract_syntax.Expr right; 
		node = ExpEq();
		while (la.kind == 22) {
			Get();
			right = ExpEq();
			node = new abstract_syntax.AndExpr(node, right); 
		}
		return node;
	}

	abstract_syntax.Expr  ExpEq() {
		abstract_syntax.Expr  node;
		abstract_syntax.Expr right; String op; 
		node = ExpRel();
		while (la.kind == 23 || la.kind == 24) {
			if (la.kind == 23) {
				Get();
			} else {
				Get();
			}
			op = t.val; 
			right = ExpRel();
			if (op.equals("=")) node = new abstract_syntax.EqExpr(node, right); else node = new abstract_syntax.NeqExpr(node, right); 
		}
		return node;
	}

	abstract_syntax.Expr  ExpRel() {
		abstract_syntax.Expr  node;
		abstract_syntax.Expr right; String op; 
		node = ExpAdd();
		while (StartOf(3)) {
			if (la.kind == 25) {
				Get();
			} else if (la.kind == 26) {
				Get();
			} else if (la.kind == 27) {
				Get();
			} else {
				Get();
			}
			op = t.val; 
			right = ExpAdd();
			if (op.equals("<")) node = new abstract_syntax.LtExpr(node, right);
			else if (op.equals("<=")) node = new abstract_syntax.LeExpr(node, right);
			else if (op.equals(">")) node = new abstract_syntax.GtExpr(node, right);
			else node = new abstract_syntax.GeExpr(node, right); 
		}
		return node;
	}

	abstract_syntax.Expr  ExpAdd() {
		abstract_syntax.Expr  node;
		abstract_syntax.Expr right; String op; 
		node = ExpMul();
		while (la.kind == 29 || la.kind == 30) {
			if (la.kind == 29) {
				Get();
			} else {
				Get();
			}
			op = t.val; 
			right = ExpMul();
			if (op.equals("+")) node = new abstract_syntax.AddExpr(node, right); else node = new abstract_syntax.SubExpr(node, right); 
		}
		return node;
	}

	abstract_syntax.Expr  ExpMul() {
		abstract_syntax.Expr  node;
		abstract_syntax.Expr right; String op; 
		node = ExpUnary();
		while (la.kind == 31 || la.kind == 32) {
			if (la.kind == 31) {
				Get();
			} else {
				Get();
			}
			op = t.val; 
			right = ExpUnary();
			if (op.equals("*")) node = new abstract_syntax.MulExpr(node, right); else node = new abstract_syntax.DivExpr(node, right); 
		}
		return node;
	}

	abstract_syntax.Expr  ExpUnary() {
		abstract_syntax.Expr  node;
		node = null; 
		if (la.kind == 33) {
			Get();
			node = ExpUnary();
			node = new abstract_syntax.NegExpr(node); 
		} else if (la.kind == 30) {
			Get();
			node = ExpUnary();
			node = new abstract_syntax.SubExpr(new abstract_syntax.NumExpr(0), node); 
		} else if (StartOf(4)) {
			node = Term();
		} else SynErr(44);
		return node;
	}

	abstract_syntax.Expr  Term() {
		abstract_syntax.Expr  node;
		node = null; String name; java.util.List<abstract_syntax.Expr> args = null; abstract_syntax.Expr arg; abstract_syntax.Type letType = null; String letName = ""; abstract_syntax.Expr letVal; abstract_syntax.Expr cond; abstract_syntax.Expr thenExpr; 
		switch (la.kind) {
		case 2: {
			Get();
			if (t.val.contains(".")) node = new abstract_syntax.NumExpr(Integer.parseInt(t.val.split("\\.")[0])); else node = new abstract_syntax.NumExpr(Integer.parseInt(t.val)); 
			break;
		}
		case 34: {
			Get();
			node = new abstract_syntax.BoolExpr(true); 
			break;
		}
		case 35: {
			Get();
			node = new abstract_syntax.BoolExpr(false); 
			break;
		}
		case 3: {
			Get();
			String s = t.val; node = new abstract_syntax.StringExpr(s.substring(1, s.length() - 1)); 
			break;
		}
		case 1: {
			Get();
			name = t.val; 
			if (la.kind == 5) {
				Get();
				args = new java.util.ArrayList<>(); 
				if (StartOf(2)) {
					arg = Exp();
					args.add(arg); 
					while (la.kind == 12) {
						Get();
						arg = Exp();
						args.add(arg); 
					}
				}
				Expect(6);
				node = new abstract_syntax.CallExpr(name, args); 
			} else if (StartOf(5)) {
				node = new abstract_syntax.VarExpr(name); 
			} else SynErr(45);
			break;
		}
		case 5: {
			Get();
			node = Exp();
			Expect(6);
			break;
		}
		case 36: {
			Get();
			cond = Exp();
			Expect(37);
			thenExpr = Exp();
			Expect(38);
			node = Exp();
			node = new abstract_syntax.IfExpr(cond, thenExpr, node); 
			break;
		}
		case 16: {
			Get();
			letType = Type();
			Expect(1);
			letName = t.val; 
			Expect(7);
			letVal = Exp();
			Expect(17);
			node = Exp();
			node = new abstract_syntax.LetExpr(letType, letName, letVal, node); 
			break;
		}
		default: SynErr(46); break;
		}
		return node;
	}



	public void Parse() {
		la = new Token();
		la.val = "";		
		Get();
		ETSL();
		Expect(0);

		scanner.buffer.Close();
	}

	private static final boolean[][] set = {
		{_T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x},
		{_x,_x,_x,_x, _x,_x,_x,_x, _x,_T,_T,_x, _x,_x,_x,_x, _x,_x,_T,_T, _T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x},
		{_x,_T,_T,_T, _x,_T,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_T,_x, _x,_T,_T,_T, _T,_x,_x,_x, _x},
		{_x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_T,_T,_T, _T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x},
		{_x,_T,_T,_T, _x,_T,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_T,_T, _T,_x,_x,_x, _x},
		{_x,_x,_x,_x, _x,_x,_T,_x, _T,_x,_x,_x, _T,_x,_x,_x, _x,_T,_x,_x, _x,_T,_T,_T, _T,_T,_T,_T, _T,_T,_T,_T, _T,_x,_x,_x, _x,_T,_T,_x, _x}

	};
} // end Parser


class Errors {
	public int count = 0;                                    // number of errors detected
	public java.io.PrintStream errorStream = System.out;     // error messages go to this stream
	public String errMsgFormat = "-- line {0} col {1}: {2}"; // 0=line, 1=column, 2=text
	
	protected void printMsg(int line, int column, String msg) {
		StringBuffer b = new StringBuffer(errMsgFormat);
		int pos = b.indexOf("{0}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, line); }
		pos = b.indexOf("{1}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, column); }
		pos = b.indexOf("{2}");
		if (pos >= 0) b.replace(pos, pos+3, msg);
		errorStream.println(b.toString());
	}
	
	public void SynErr (int line, int col, int n) {
		String s;
		switch (n) {
			case 0: s = "EOF expected"; break;
			case 1: s = "id expected"; break;
			case 2: s = "num expected"; break;
			case 3: s = "string expected"; break;
			case 4: s = "\"function\" expected"; break;
			case 5: s = "\"(\" expected"; break;
			case 6: s = "\")\" expected"; break;
			case 7: s = "\":=\" expected"; break;
			case 8: s = "\";\" expected"; break;
			case 9: s = "\"event\" expected"; break;
			case 10: s = "\"agent\" expected"; break;
			case 11: s = "\"on\" expected"; break;
			case 12: s = "\",\" expected"; break;
			case 13: s = "\"log\" expected"; break;
			case 14: s = "\"call\" expected"; break;
			case 15: s = "\"skip\" expected"; break;
			case 16: s = "\"let\" expected"; break;
			case 17: s = "\"in\" expected"; break;
			case 18: s = "\"num\" expected"; break;
			case 19: s = "\"bool\" expected"; break;
			case 20: s = "\"string\" expected"; break;
			case 21: s = "\"||\" expected"; break;
			case 22: s = "\"&&\" expected"; break;
			case 23: s = "\"=\" expected"; break;
			case 24: s = "\"!=\" expected"; break;
			case 25: s = "\"<\" expected"; break;
			case 26: s = "\"<=\" expected"; break;
			case 27: s = "\">\" expected"; break;
			case 28: s = "\">=\" expected"; break;
			case 29: s = "\"+\" expected"; break;
			case 30: s = "\"-\" expected"; break;
			case 31: s = "\"*\" expected"; break;
			case 32: s = "\"/\" expected"; break;
			case 33: s = "\"!\" expected"; break;
			case 34: s = "\"true\" expected"; break;
			case 35: s = "\"false\" expected"; break;
			case 36: s = "\"if\" expected"; break;
			case 37: s = "\"then\" expected"; break;
			case 38: s = "\"else\" expected"; break;
			case 39: s = "??? expected"; break;
			case 40: s = "invalid Decl"; break;
			case 41: s = "invalid Decl"; break;
			case 42: s = "invalid Type"; break;
			case 43: s = "invalid Action"; break;
			case 44: s = "invalid ExpUnary"; break;
			case 45: s = "invalid Term"; break;
			case 46: s = "invalid Term"; break;
			default: s = "error " + n; break;
		}
		printMsg(line, col, s);
		count++;
	}

	public void SemErr (int line, int col, String s) {	
		printMsg(line, col, s);
		count++;
	}
	
	public void SemErr (String s) {
		errorStream.println(s);
		count++;
	}
	
	public void Warning (int line, int col, String s) {	
		printMsg(line, col, s);
	}
	
	public void Warning (String s) {
		errorStream.println(s);
	}
} // Errors


class FatalError extends RuntimeException {
	public static final long serialVersionUID = 1L;
	public FatalError(String s) { super(s); }
}
