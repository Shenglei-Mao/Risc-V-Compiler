package assembly;

import java.util.List;

import compiler.Scope.SymbolTableEntry;
import ast.visitor.AbstractASTVisitor;

import ast.*;
import ast.BinaryOpNode.OpType;
import assembly.instructions.*;
import compiler.Scope;

public class CodeGenerator extends AbstractASTVisitor<CodeObject> {

	int intRegCount;
	int floatRegCount;
	static final public char intTempPrefix = 't';
	static final public char floatTempPrefix = 'f';
	
	int loopLabel;
	int elseLabel;
	int outLabel;
	
	public CodeGenerator() {
		loopLabel = 0;
		elseLabel = 0;
		outLabel = 0;
		intRegCount = 0;		
		floatRegCount = 0;
	}

	public int getIntRegCount() {
		return intRegCount;
	}

	public int getFloatRegCount() {
		return floatRegCount;
	}
	
	/**
	 * Generate code for Variables
	 * 
	 * Create a code object that just holds a variable
	 * 
	 * Important: add a pointer from the code object to the symbol table entry
	 *            so we know how to generate code for it later (we'll need to find
	 *            the address)
	 * 
	 * Mark the code object as holding a variable, and also as an lval
	 */
	@Override
	protected CodeObject postprocess(VarNode node) {
		
		Scope.SymbolTableEntry sym = node.getSymbol();
		
		CodeObject co = new CodeObject(sym);
		co.lval = true;
		co.type = node.getType();

		return co;
	}

	/** Generate code for IntLiterals
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(IntLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a register
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new Li(generateTemp(Scope.Type.INT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/** Generate code for FloatLiteras
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(FloatLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a regisster
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new FImm(generateTemp(Scope.Type.FLOAT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for binary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from left child
	 * Step 1a: if left child is an lval, add a load to get the data
	 * Step 2: add code from right child
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate binary operation using temps from left and right
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(BinaryOpNode node, CodeObject left, CodeObject right) {

		CodeObject co = new CodeObject();
		if (left.lval) {
			left = rvalify(left);
		}
		co.code.addAll(left.code);
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);
		Instruction calcb = null;
		String lt = left.temp;
		String rt = right.temp;
		if (left.type == Scope.Type.INT && right.type == Scope.Type.INT){
			switch (node.getOp()) {
				case ADD:
					calcb = new Add(lt, rt, generateTemp(left.type));
					break;
				case SUB:
					calcb = new Sub(lt, rt, generateTemp(left.type));
					break;
				case MUL:
					calcb = new Mul(lt, rt, generateTemp(left.type));
					break;
				case DIV:
					calcb = new Div(lt, rt, generateTemp(left.type));
					break;
				default : break;
			}
		}
		if (left.type == Scope.Type.FLOAT && right.type == Scope.Type.FLOAT){
			switch (node.getOp()) {
				case ADD:
					calcb = new FAdd(lt, rt, generateTemp(left.type));
					break;
				case SUB:
					calcb = new FSub(lt, rt, generateTemp(left.type));
					break;
				case MUL:
					calcb = new FMul(lt, rt, generateTemp(left.type));
					break;
				case DIV:
					calcb = new FDiv(lt, rt, generateTemp(left.type));
					break;
				default : break;
			}
		}
		co.code.add(calcb);
		co.temp = calcb.getDest();
		co.type = left.type;
		co.lval = false;
		return co;
	}

	/**
	 * Generate code for unary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from child expression
	 * Step 1a: if child is an lval, add a load to get the data
	 * Step 2: generate instruction to perform unary operation
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(UnaryOpNode node, CodeObject expr) {
		
		CodeObject co = new CodeObject();
		if (expr.lval) {
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);
		Instruction calcu = null;
		String un1 = expr.temp;
		if (node.getOp() == UnaryOpNode.OpType.NEG){
			if (expr.type == Scope.Type.INT){
				calcu = new Neg(un1, generateTemp(expr.type));
			}
			if (expr.type == Scope.Type.FLOAT){
				calcu = new FNeg(un1, generateTemp(expr.type));
			}
		}
		co.code.add(calcu);
		co.temp = calcu.getDest();
		co.type = expr.type;
		co.lval = false;
		return co;

	}

	/**
	 * Generate code for assignment statements
	 * 
	 * Step 0: create new code object
	 * Step 1: if LHS is a variable, generate a load instruction to get the address into a register
	 * Step 1a: add code from LHS of assignment (make sure it results in an lval!)
	 * Step 2: add code from RHS of assignment
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate store
	 * 
	 * Hint: it is going to be easiest to just generate a store with a 0 immediate
	 * offset, and the complete store address in a register:
	 * 
	 * sw rhs 0(lhs)
	 */
	@Override
	protected CodeObject postprocess(AssignNode node, CodeObject left,
			CodeObject right) {
		
		CodeObject co = new CodeObject();
		assert(left.lval == true);
		if (left.isVar()) {
			left = generateAddrFromVariable(left);
		}
		co.code.addAll(left.code);
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);
		if (right.type == Scope.Type.INT){
			Instruction assr = new Sw(right.temp, left.temp, "0");
			co.code.add(assr);
			co.temp = assr.getDest();
		}
		if (right.type == Scope.Type.FLOAT){
			Instruction assr = new Fsw(right.temp, left.temp, "0");
			co.code.add(assr);
			co.temp = assr.getDest();
		}
		co.lval = false;
		return co;

	}

	/**
	 * Add together all the lists of instructions generated by the children
	 */
	@Override
	protected CodeObject postprocess(StatementListNode node,
			List<CodeObject> statements) {
		CodeObject co = new CodeObject();
		//add the code from each individual statement
		for (CodeObject subcode : statements) {
			co.code.addAll(subcode.code);
		}
		co.type = null; //set to null to trigger errors
		return co;
	}
	
	/**
	 * Generate code for read
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from VarNode (make sure it's an lval)
	 * Step 2: generate GetI instruction, storing into temp
	 * Step 3: generate store, to store temp in variable
	 */
	@Override
	protected CodeObject postprocess(ReadNode node, CodeObject var) {
		
		//Step 0
		CodeObject co = new CodeObject();

		//Generating code for read(id)
		assert(var.getSTE() != null); //var had better be a variable
		CodeObject tmp = generateAddrFromVariable(var);

		co.code.addAll(tmp.code);

		InstructionList il = new InstructionList();
		switch(node.getType()) {
			case INT: 
				//Code to generate if INT:
				//geti tmp
				//sw tmp 0(var.tmp)
				Instruction geti = new GetI(generateTemp(Scope.Type.INT));
				il.add(geti);
				Instruction sw = new Sw(geti.getDest(), tmp.temp, "0");
				il.add(sw);
				break;
			case FLOAT:
				//Code to generate if FLOAT:
				//getf tmp
				//fsw tmp 0(var.tmp)
				Instruction getf = new GetF(generateTemp(Scope.Type.FLOAT));
				il.add(getf);
				Instruction fsw = new Fsw(getf.getDest(), tmp.temp, "0");
				il.add(fsw);
				break;
			default:
				throw new Error("Shouldn't read into other variable");
		}
		
		co.code.addAll(il);

		co.lval = false; //doesn't matter
		co.temp = null; //set to null to trigger errors
		co.type = null; //set to null to trigger errors

		return co;
	}

	/**
	 * Generate code for print
	 * 
	 * Step 0: create new code object
	 * 
	 * If printing a string:
	 * Step 1: add code from expression to be printed (make sure it's an lval)
	 * Step 2: generate a PutS instruction printing the result of the expression
	 * 
	 * If printing an integer:
	 * Step 1: add code from the expression to be printed
	 * Step 1a: if it's an lval, generate a load to get the data
	 * Step 2: Generate PutI that prints the temporary holding the expression
	 */
	@Override
	protected CodeObject postprocess(WriteNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		//generating code for write(expr)

		//for strings, we expect a variable
		if (node.getWriteExpr().getType() == Scope.Type.STRING) {
			//Step 1:
			assert(expr.getSTE() != null);
			
			//Get the address of the variable
			CodeObject addrCo = generateAddrFromVariable(expr);
			co.code.addAll(addrCo.code);

			//Step 2:
			Instruction write = new PutS(addrCo.temp);
			co.code.add(write);
		} else {
			//Step 1a:
			//if expr is an lval, load from it
			if (expr.lval == true) {
				expr = rvalify(expr);
			}
			
			//Step 1:
			co.code.addAll(expr.code);

			//Step 2:
			//if type of writenode is int, use puti, if float, use putf
			Instruction write = null;
			switch(node.getWriteExpr().getType()) {
			case STRING: throw new Error("Shouldn't have a STRING here");
			case INT: write = new PutI(expr.temp); break;
			case FLOAT: write = new PutF(expr.temp); break;
			default: throw new Error("WriteNode has a weird type");
			}

			co.code.add(write);
		}

		co.lval = false; //doesn't matter
		co.temp = null; //set to null to trigger errors
		co.type = null; //set to null to trigger errors

		return co;
	}

	/**
	 * FILL IN FOR STEP 3
	 * 
	 * Generating an instruction sequence for a conditional expression
	 * 
	 * Implement this however you like. One suggestion:
	 *
	 * Create the code for the left and right side of the conditional, but defer
	 * generating the branch until you process IfStatementNode or WhileNode (since you
	 * do not know the labels yet). Modify CodeObject so you can save the necessary
	 * information to generate the branch instruction in IfStatementNode or WhileNode
	 * 
	 * Alternate idea 1:
	 * 
	 * Don't do anything as part of CodeGenerator. Create a new visitor class
	 * that you invoke *within* your processing of IfStatementNode or WhileNode
	 * 
	 * Alternate idea 2:
	 * 
	 * Create the branch instruction in this function, then tweak it as necessary in
	 * IfStatementNode or WhileNode
	 * 
	 * Hint: you may need to preserve extra information in the returned CodeObject to
	 * make sure you know the type of branch code to generate (int vs float)
	 */
	@Override
	protected CodeObject postprocess(CondNode node, CodeObject left, CodeObject right) {
		CodeObject co = new CodeObject();
		/* FILL IN */
		/* Using Alternate idea2 */
		if (left.lval) {
			left = rvalify(left);
		}
		co.code.addAll(left.code);
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);
		assert(left.type == right.type);
		co.type = left.type;
		co.temp = left.temp + ":" + node.getOp().toString() + ":" + right.temp;
		return co;
	}

	/**
	 * FILL IN FOR STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		<cond code>
	 *		<flipped branch> elseLabel
	 *		<then code>
	 *		j outLabel
	 *		elseLabel:
	 *		<else code>
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 */
	@Override
	protected CodeObject postprocess(IfStatementNode node, CodeObject cond, CodeObject tlist, CodeObject elist) {
		//Step 0:
		CodeObject co = new CodeObject();
		//generate labels
		String elseLabel = generateElseLabel();
		String outLabel = generateOutLabel();
		//add code
		co.code.addAll(cond.code);
		//branch
		String[] info = cond.temp.split(":");
		String left = info[0], opType = info[1], right = info[2];
		Instruction instr = null;
		if (cond.type == Scope.Type.INT) {
			switch (opType) {
				case "EQ":
					instr = new Bne(left, right, elseLabel);
					break;
				case "NE":
					instr = new Beq(left, right, elseLabel);
					break;
				case "LT":
					instr = new Bge(left, right, elseLabel);
					break;
				case "LE":
					instr = new Bgt(left, right, elseLabel);
					break;
				case "GT":
					instr = new Ble(left, right, elseLabel);
					break;
				case "GE":
					instr = new Blt(left, right, elseLabel);
					break;
				default:
					throw new Error("Condition node has a weird opType");
			}
		} else if (cond.type == Scope.Type.FLOAT) {
			switch (opType) {
				case "EQ":
					instr = new Feq(left, right, elseLabel);
					break;
				case "NE":
					instr = new Feq(left, right, elseLabel);
					break;
				case "LT":
					instr = new Fle(right, left, elseLabel);
					break;
				case "LE":
					instr = new Flt(right, left, elseLabel);
					break;
				case "GT":
					instr = new Fle(left, right, elseLabel);
					break;
				case "GE":
					instr = new Flt(left, right, elseLabel);
					break;
			}
		} else {
			throw new Error("Condition node not either int or float");
		}
		co.code.add(instr);
		co.code.addAll(tlist.code);
		co.code.add(new J(outLabel));
		co.code.add(new Label(elseLabel));	
		co.code.addAll(elist.code);
		co.code.add(new Label(outLabel));
		return co;
	}

		/**
	 * FILL IN FOR STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		loopLabel:
	 *		<cond code>
	 *		<flipped branch> outLabel
	 *		<body code>
	 *		j loopLabel
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 */
	@Override
	protected CodeObject postprocess(WhileNode node, CodeObject cond, CodeObject slist) {
		//Step 0:
		CodeObject co = new CodeObject();

		/* FILL IN */
		String loopLabel = generateLoopLabel();
		String outLabel = generateOutLabel();
		co.code.add(new Label(loopLabel));
		co.code.addAll(cond.code);
		String[] info = cond.temp.split(":");
		String left = info[0], opType = info[1], right = info[2];
		Instruction instr = null;

		if (cond.type == Scope.Type.INT) {
			switch (opType) {
				case "EQ":
					instr = new Bne(left, right, outLabel);
					break;
				case "NE":
					instr = new Beq(left, right, outLabel);
					break;
				case "LT":
					instr = new Bge(left, right, outLabel);
					break;
				case "LE":
					instr = new Bgt(left, right, outLabel);
					break;
				case "GT":
					instr = new Ble(left, right, outLabel);
					break;
				case "GE":
					instr = new Blt(left, right, outLabel);
					break;
				default:
					throw new Error("Condition node has a weird opType");
			}
		} else if (cond.type == Scope.Type.FLOAT) {
			switch (opType) {
				case "EQ":
					instr = new Feq(left, right, outLabel);
					break;
				case "NE":
					instr = new Feq(left, right, outLabel);
					break;
				case "LT":
					instr = new Fle(right, left, outLabel);
					break;
				case "LE":
					instr = new Flt(right, left, outLabel);
					break;
				case "GT":
					instr = new Fle(left, right, outLabel);
					break;
				case "GE":
					instr = new Flt(left, right, outLabel);
					break;
			}
			throw new Error("float type haven't supported");
		} else {
			throw new Error("Condition node not either int or float");
		}
		co.code.add(instr);
		co.code.addAll(slist.code);
		co.code.add(new J(loopLabel));
		co.code.add(new Label(outLabel));
		return co;
	}

	/**
	 * Generating code for returns
	 * 
	 * For now, we don't do anything with return values, so just generate HALT
	 */
	@Override
	protected CodeObject postprocess(ReturnNode node, CodeObject retExpr) {
		CodeObject co = new CodeObject();

		//if retexpr is an lval, load from it
		if (retExpr.lval) {
			retExpr = rvalify(retExpr);
		}

		co.code.addAll(retExpr.code);

		//We don't support functions yet, so a return is just a halt
		Instruction h = new Halt();
		co.code.add(h);
		co.type = null; //set to null to trigger errors

		return co;
	}
	
	/**
	 * Generate a fresh temporary
	 * 
	 * @return new temporary register name
	 */
	protected String generateTemp(Scope.Type t) {
		switch(t) {
			case INT: return intTempPrefix + String.valueOf(++intRegCount);
			case FLOAT: return floatTempPrefix + String.valueOf(++floatRegCount);
			default: throw new Error("Generating temp for bad type");
		}
	}

	protected String generateLoopLabel() {
		return "loop_" + String.valueOf(++loopLabel);
	}

	protected String generateElseLabel() {
		return  "else_" + String.valueOf(++elseLabel);
	}

	protected String generateOutLabel() {
		return "out_" +  String.valueOf(++outLabel);
	}
	
	/**
	 * Take a code object that results in an lval, and create a new code
	 * object that adds a load to generate the rval.
	 * 
	 * @param lco The code object resulting in an address
	 * @return A code object with all the code of <code>lco</code> followed by a load
	 *         to generate an rval
	 */
	protected CodeObject rvalify(CodeObject lco) {
		
		assert (lco.lval);
		CodeObject co = new CodeObject();
		if (lco.isVar())  {
			lco = generateAddrFromVariable(lco);
		}
		co.code.addAll(lco.code);
		if (lco.type == Scope.Type.INT){
			Instruction instr = new Lw(generateTemp(lco.type), lco.temp, "0");
			co.code.add(instr);
			co.temp = instr.getDest();
		}
		if (lco.type == Scope.Type.FLOAT){
			Instruction instr = new Flw(generateTemp(lco.type), lco.temp, "0");
			co.code.add(instr);
			co.temp = instr.getDest();
		}
		co.lval = false;
		co.type = lco.type;
		return co;
	}

	/**
	 * Take a code object that holds just a variable and turn it into a code object
	 * that places the address of that variable into a registers
	 * 
	 * @param lco The code object holding a variable
	 * @return A code object with the variable's address in a register
	 */
	private CodeObject generateAddrFromVariable(CodeObject lco) {

		CodeObject co = new CodeObject();

		//Step 1:
		SymbolTableEntry symbol = lco.getSTE();
		String address = String.valueOf(symbol.getAddress());

		//Step 2:
		//la tmp' addr //Register type needs to be an int
		Instruction loadAddr = new La(generateTemp(Scope.Type.INT), address);

		co.code.add(loadAddr); //add instruction to code object
		co.lval = true; //co holds an lval, because it's an address
		co.temp = loadAddr.getDest(); //temp is in destination of la
		co.ste = null; //not a variable
		co.type = symbol.getType(); //even though register type is an int, address points to Type

		return co;
	}

}
