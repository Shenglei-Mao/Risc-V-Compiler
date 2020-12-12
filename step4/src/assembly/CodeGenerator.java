package assembly;

import java.util.List;

import compiler.Scope.SymbolTableEntry;
import ast.visitor.AbstractASTVisitor;

import ast.*;
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

	String currFunc;
	
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
//
//		CodeObject co = new CodeObject();
//		assert(left.lval == true);
//		if (left.isVar()) {
//			left = generateAddrFromVariable2(left);
//		}
//		co.code.addAll(left.code);
//		if (right.lval) {
//			right = rvalify(right);
//		}
//		co.code.addAll(right.code);
//		if (right.type == Scope.Type.INT){
//			Instruction assr = new Sw(right.temp, left.temp, "0");
//			co.code.add(assr);
//			co.temp = assr.getDest();
//		}
//		if (right.type == Scope.Type.FLOAT){
//			Instruction assr = new Fsw(right.temp, left.temp, "0");
//			co.code.add(assr);
//			co.temp = assr.getDest();
//		}
//		co.lval = false;
//		return co;

		//Assume right callnode only
		CodeObject co = new CodeObject();
		assert(left.lval == true);
//		if (left.isVar()) {
//			left = generateAddrFromVariable2(left);
//		}
		co.code.addAll(left.code);
		if (right.lval) {
			right = rvalify(right);
		}

//		int offset = left.getSTE().getAddress();
		String address = left.getSTE().addressToString();
		if (left.type == Scope.Type.INT){
			Instruction assr = null;
			if (left.getSTE().isLocal()) {
				assr = new Sw(right.temp, "fp", address);
				co.code.addAll(right.code);
			} else {
				String dest = generateTemp(Scope.Type.INT);
				co.code.add(new La(dest, address));
				co.code.addAll(right.code);
				assr = new Sw(right.temp, dest, "0");
			}
			co.code.add(assr);
			co.temp = assr.getDest();
		}
		if (left.type == Scope.Type.FLOAT) {
			Instruction assr = null;
			if (left.getSTE().isLocal()) {
				assr = new Fsw(right.temp, "fp", address);
				co.code.addAll(right.code);
			} else {
				String dest = generateTemp(Scope.Type.INT);
				co.code.add(new La(dest, address));
				co.code.addAll(right.code);
				assr = new Fsw(right.temp, dest, "0");
			}
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

		InstructionList il = new InstructionList();
		switch(node.getType()) {
			case INT: 
				//Code to generate if INT:
				//geti tmp
				//if var is global: la tmp', <var>; sw tmp 0(tmp')
				//if var is local: sw tmp offset(fp)
				Instruction geti = new GetI(generateTemp(Scope.Type.INT));
				il.add(geti);
				InstructionList store = new InstructionList();
				if (var.getSTE().isLocal()) {
					store.add(new Sw(geti.getDest(), "fp", String.valueOf(var.getSTE().addressToString())));
				} else {
					store.addAll(generateAddrFromVariable(var));
					store.add(new Sw(geti.getDest(), store.getLast().getDest(), "0"));
				}
				il.addAll(store);
				break;
			case FLOAT:
				//Code to generate if FLOAT:
				//getf tmp
				//if var is global: la tmp', <var>; fsw tmp 0(tmp')
				//if var is local: fsw tmp offset(fp)
				Instruction getf = new GetF(generateTemp(Scope.Type.FLOAT));
				il.add(getf);
				InstructionList fstore = new InstructionList();
				if (var.getSTE().isLocal()) {
					fstore.add(new Fsw(getf.getDest(), "fp", String.valueOf(var.getSTE().addressToString())));
				} else {
					fstore.addAll(generateAddrFromVariable(var));
					fstore.add(new Fsw(getf.getDest(), fstore.getLast().getDest(), "0"));
				}
				il.addAll(fstore);
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
			
			System.out.println("; generating code to print " + expr.getSTE());

			//Get the address of the variable
			InstructionList addrCo = generateAddrFromVariable(expr);
			co.code.addAll(addrCo);

			//Step 2:
			Instruction write = new PutS(addrCo.getLast().getDest());
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
		if (elist.code.isEmpty()){
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
				co.code.add(instr);
			} else if (cond.type == Scope.Type.FLOAT) {
				String temp = generateTemp(Scope.Type.INT);
				//String temp0 = generateTemp(Scope.Type.INT);
				InstructionList branchInstr = new InstructionList();
				//branchInstr.add(new Li(temp0, "0"));
				//need to check the conditions ...
				switch (opType) {
					case "EQ":
						branchInstr.add(new Feq(left, right, temp));
						branchInstr.add(new Beq(temp, "x0", outLabel));
						break;
					case "NE":
						branchInstr.add(new Feq(left, right, temp));
						branchInstr.add(new Beq(temp, "x0", outLabel));
						break;
					case "LT":
						branchInstr.add(new Fle(right, left, temp));
						branchInstr.add(new Beq(temp, "x0", outLabel));
						break;
					case "LE":
						branchInstr.add(new Flt(right, left, temp));
						branchInstr.add(new Bne(temp, "x0", outLabel));
						break;
					case "GT":
						branchInstr.add(new Fle(left, right, temp));
						branchInstr.add(new Beq(temp, "x0", outLabel));
						break;
					case "GE":
						branchInstr.add(new Flt(left, right, temp));
						branchInstr.add(new Bne(temp, "x0", outLabel));
						break;
				}
				co.code.addAll(branchInstr);
			} else {
				throw new Error("Condition node not either int or float");
			}
			co.code.addAll(tlist.code);
			co.code.addAll(elist.code);
			co.code.add(new Label(outLabel));
		} else { 		
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
				co.code.add(instr);
				
			} else if (cond.type == Scope.Type.FLOAT) {
				String temp = generateTemp(Scope.Type.INT);
				//String temp0 = generateTemp(Scope.Type.INT);
				InstructionList branchInstr = new InstructionList();
				//branchInstr.add(new Li(temp0, "0"));
				//need to check the conditions ...
				switch (opType) {
					case "EQ":
						branchInstr.add(new Feq(left, right, temp));
						branchInstr.add(new Beq(temp, "x0", elseLabel));
						break;
					case "NE":
						branchInstr.add(new Feq(left, right, temp));
						branchInstr.add(new Beq(temp, "x0", elseLabel));
						break;
					case "LT":
						branchInstr.add(new Fle(right, left, temp));
						branchInstr.add(new Beq(temp, "x0", elseLabel));
						break;
					case "LE":
						branchInstr.add(new Flt(right, left, temp));
						branchInstr.add(new Bne(temp, "x0", elseLabel));
						break;
					case "GT":
						branchInstr.add(new Fle(left, right, temp));
						branchInstr.add(new Beq(temp, "x0", elseLabel));
						break;
					case "GE":
						branchInstr.add(new Flt(left, right, temp));
						branchInstr.add(new Bne(temp, "x0", elseLabel));
						break;
				}
				co.code.addAll(branchInstr);
			} else {
				throw new Error("Condition node not either int or float");
			}
			co.code.addAll(tlist.code);
			co.code.add(new J(outLabel));
			co.code.add(new Label(elseLabel));	
			co.code.addAll(elist.code);
			co.code.add(new Label(outLabel));
		}
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
			co.code.add(instr);
		} else if (cond.type == Scope.Type.FLOAT) {
			String temp = generateTemp(Scope.Type.INT);
			InstructionList branchInstr = new InstructionList();
			switch (opType) {
				case "EQ":
					branchInstr.add(new Feq(left, right, temp));
					branchInstr.add(new Beq(temp, "x0", outLabel));
					break;
				case "NE":
					branchInstr.add(new Feq(left, right, temp));
					branchInstr.add(new Beq(temp, "x0", outLabel));
					break;
				case "LT":
					branchInstr.add(new Fle(right, left, temp));
					branchInstr.add(new Beq(temp, "x0", outLabel));
					break;
				case "LE":
					branchInstr.add(new Flt(right, left, temp));
					branchInstr.add(new Bne(temp, "x0", outLabel));
					break;
				case "GT":
					branchInstr.add(new Fle(left, right, temp));
					branchInstr.add(new Beq(temp, "x0", outLabel));
					break;
				case "GE":
					branchInstr.add(new Flt(left, right, temp));
					branchInstr.add(new Bne(temp, "x0", outLabel));
					break;
			}
			co.code.addAll(branchInstr);
		} else {
			throw new Error("Condition node not either int or float");
		}
		
		co.code.addAll(slist.code);
		co.code.add(new J(loopLabel));
		co.code.add(new Label(outLabel));
		return co;
	}

	//push
	protected InstructionList pushRegisteri(String s){
		InstructionList instrList = new InstructionList();
		instrList.add(new Sw(s, "sp", "0"));
		instrList.add(new Addi("sp", "-4", "sp"));
		return instrList;
	}

	protected InstructionList pushRegisterf(String s){
		InstructionList instrList = new InstructionList();
		instrList.add(new Fsw(s, "sp", "0"));
		instrList.add(new Addi("sp", "-4", "sp"));
		return instrList;
	}

	//pop
	protected InstructionList popRegisteri(String s){
		InstructionList instrList = new InstructionList();
		instrList.add(new Addi("sp", "4", "sp"));
		instrList.add(new Lw(s, "sp", "0"));
		return instrList;
	}

	protected InstructionList popRegisterf(String s){
		InstructionList instrList = new InstructionList();
		instrList.add(new Addi("sp", "4", "sp"));
		instrList.add(new Flw(s, "sp", "0"));
		return instrList;
	}

	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generating code for returns
	 * 
	 * Step 0: Generate new code object
	 * 
	 * Step 1: Add retExpr code to code object (rvalify if necessary)
	 * 
	 * Step 2: Store result of retExpr in appropriate place on stack (fp + 8)
	 * 
	 * Step 3: Jump to out label (use @link{generateFunctionOutLabel()})
	 */
	@Override
	protected CodeObject postprocess(ReturnNode node, CodeObject retExpr) {
		CodeObject co = new CodeObject();
		//1
		if (retExpr.lval){
			retExpr = rvalify(retExpr);
		}
		co.code.addAll(retExpr.code);
		//2
		if (retExpr.type == Scope.Type.INT){
			co.code.add(new Sw(retExpr.temp, "fp", "8"));
		}
		if (retExpr.type == Scope.Type.FLOAT){
			co.code.add(new Fsw(retExpr.temp, "fp", "8"));
		}
		//3
		String generateFuncOutLabel = generateFunctionOutLabel();
		co.code.add(new J(generateFuncOutLabel));
		co.type = retExpr.getType();
		co.lval = false;
		return co;
	}

	@Override
	protected void preprocess(FunctionNode node) {
		// Generate function label information, used for other labels inside function
		currFunc = node.getFuncName();

		//reset register counts; each function uses new registers!
		intRegCount = 0;
		floatRegCount = 0;
	}

	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generate code for functions
	 * 
	 * Step 1: add the label for the beginning of the function
	 * 
	 * Step 2: manage frame  pointer
	 * 			a. Save old frame pointer
	 * 			b. Move frame pointer to point to base of activation record (current sp)
	 * 			c. Update stack pointer
	 * 
	 * Step 3: allocate new stack frame (use scope infromation from FunctionNode)
	 * 
	 * Step 4: save registers on stack (Can inspect intRegCount and floatRegCount to know what to save)
	 * 
	 * Step 5: add the code from the function body
	 * 
	 * Step 6: add post-processing code:
	 * 			a. Label for `return` statements inside function body to jump to
	 * 			b. Restore registers
	 * 			c. Deallocate stack frame (set stack pointer to frame pointer)
	 * 			d. Reset fp to old location
	 * 			e. Return from function
	 */
	@Override
	protected CodeObject postprocess(FunctionNode node, CodeObject body) {
		CodeObject co = new CodeObject();
		//1
		String funcLabel =  generateFunctionLabel();
		co.code.add(new Label(funcLabel));
		//2
		co.code.add(new Sw("fp", "sp", "0"));
		co.code.add(new Mv("sp","fp"));
		co.code.add(new Addi("sp", "-4", "sp"));
		//3
		String offset = node.getScope().getNumLocals() == 0? "-0": String.valueOf(-4 * node.getScope().getNumLocals());
		co.code.add(new Addi("sp", offset, "sp"));
		//4
		for (int j = 1; j <= intRegCount; j++){
			co.code.addAll(pushRegisteri("t" + j));
		}
		for (int j = 1; j <= floatRegCount; j++){
			co.code.addAll(pushRegisterf("f" + j));
		}
		//5
		if (body.lval){
			body = rvalify(body);
		}
		co.code.addAll(body.code);
		//6
		String postProcessLabel = generateFunctionOutLabel();
		co.code.add(new Label(postProcessLabel));
		for (int j = floatRegCount; j >= 1; j--){
			co.code.addAll(popRegisterf("f" + j));
		}
		for (int j = intRegCount; j >= 1; j--){
			co.code.addAll(popRegisteri("t" + j));
		}

		co.code.add(new Mv("fp", "sp"));
		co.code.add(new Lw("fp", "fp", "0"));
		co.code.add(new Ret());
		co.type = body.getType();
		co.lval = false;
		return co;
	}

	/**
	 * Generate code for the list of functions. This is the "top level" code generation function
	 * 
	 * Step 1: Set fp to point to sp
	 * 
	 * Step 2: Insert a JR to main
	 * 
	 * Step 3: Insert a HALT
	 * 
	 * Step 4: Include all the code of the functions
	 */
	@Override
	protected CodeObject postprocess(FunctionListNode node, List<CodeObject> funcs) {
		CodeObject co = new CodeObject();

		co.code.add(new Mv("sp", "fp"));
		co.code.add(new Jr(generateFunctionLabel("main")));
		co.code.add(new Halt());
		co.code.add(new Blank());

		//add code for each of the functions
		for (CodeObject c : funcs) {
			co.code.addAll(c.code);
			co.code.add(new Blank());
		}

		return co;
	}

	/**
	* 
	* FILL IN FOR STEP 4
	* 
	* Generate code for a call expression
	 * 
	 * Step 1: For each argument:
	 * 
	 * 	Step 1a: insert code of argument (don't forget to rvalify!)
	 * 
	 * 	Step 1b: push result of argument onto stack 
	 * 
	 * Step 2: alloate space for return value
	 * 
	 * Step 3: push current return address onto stack
	 * 
	 * Step 4: jump to function
	 * 
	 * Step 5: pop return address back from stack
	 * 
	 * Step 6: pop return value into fresh temporary (destination of call expression)
	 * 
	 * Step 7: remove arguments from stack (move sp)
	 */
	@Override
	protected CodeObject postprocess(CallNode node, List<CodeObject> args) {
		//STEP 0
		CodeObject co = new CodeObject();
		//1
		int cntI = 0;
		int cntF = 0;
		for (CodeObject c : args) {
			if (c.lval) {
				c = rvalify(c);
			}
			co.code.addAll(c.code);
			//String dest = generateTemp(c.type);
			if (c.type == Scope.Type.INT) {
				cntI++;
				//co.code.add(new Lw(dest, "fp", String.valueOf(-4 * cntI)));//loop counter
//				co.code.add(new Sw(dest, "fp", "0"));
//				co.code.add(new Addi("sp", "-4", "sp"));
				co.code.addAll(pushRegisteri(c.temp));

			}
			if (c.type == Scope.Type.FLOAT){
				cntF++;
				//co.code.add(new Lw(dest, "fp", String.valueOf(-4 * cntF)));//loop counter
//				co.code.add(new Fsw(dest, "fp", "0"));
//				co.code.add(new Addi("sp", "-4", "sp"));
				co.code.addAll(pushRegisterf(c.temp));
			}
		}
		//2
		co.code.add(new Addi("sp", "-4", "sp"));
		//Step 3: push current return address onto stack
//		if (node.getType() == Scope.Type.INT){
//			co.code.addAll(pushRegisteri("ra"));
//		}
//		if (node.getType() == Scope.Type.FLOAT){
//			co.code.addAll(pushRegisterf("ra"));
//		}
		co.code.addAll(pushRegisteri("ra"));
		//4
		co.code.add(new Jr(generateFunctionLabel(node.getFuncName())));
		//Step 5: pop return address back from stack
//		if (node.getType() == Scope.Type.INT){
//			co.code.addAll(popRegisteri("ra"));
//		}
//		if (node.getType() == Scope.Type.FLOAT){
//			co.code.addAll(popRegisterf("ra"));
//		}
		co.code.addAll(popRegisteri("ra"));
		//Step 6: pop return value into fresh temporary (destination of call expression)
		String returnTemp = generateTemp(node.getType());
		if (node.getType() == Scope.Type.INT){
			co.code.addAll(popRegisteri(returnTemp));
		}
		if (node.getType() == Scope.Type.FLOAT){
			co.code.addAll(popRegisterf(returnTemp));
		}
		//Step 7: remove arguments from stack (move sp)
		co.code.add(new Addi("sp", String.valueOf(4 * args.size()), "sp"));
		co.type = node.getType();
		co.lval = false;
		co.temp = returnTemp;
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

	protected String generateFunctionLabel() {
		return "func_" + currFunc;
	}

	protected String generateFunctionLabel(String func) {
		return "func_" + func;
	}

	protected String generateFunctionOutLabel() {
		return "func_ret_" + currFunc;
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
//		if (lco.isVar()){
//			co.code.addAll(generateAddrFromVariable(lco));
//		} else {
//			co.code.addAll(lco.code);
//		}
		co.code.addAll(lco.code);
//		int offset = 0;
//		if (lco.getSTE().isLocal()) {
//			offset = lco.getSTE().getAddress();
//		} else {
//			offset = lco.getSTE().
//		}
		String address = lco.getSTE().addressToString();
		if (lco.type == Scope.Type.INT){
			Instruction instr = null;
			if (lco.getSTE().isLocal()) {
				instr = new Lw(generateTemp(lco.type), "fp", address);
			} else {
				String dest = generateTemp(Scope.Type.INT);
				co.code.add(new La(dest, address));
				instr = new Lw(generateTemp(lco.type), dest, "0");
			}
			co.code.add(instr);
			co.temp = instr.getDest();
		}
		if (lco.type == Scope.Type.FLOAT){
			Instruction instr = null;
			if (lco.getSTE().isLocal()) {
				instr = new Flw(generateTemp(lco.type), "fp", address);
			} else {
				String dest = generateTemp(Scope.Type.INT);
				co.code.add(new La(dest, address));
				if (lco.type == Scope.Type.INT) {
					instr = new Lw(generateTemp(lco.type), dest, "0");
				} else {
					instr = new Flw(generateTemp(lco.type), dest, "0");
				}
			}
			co.code.add(instr);
			co.temp = instr.getDest();
		}
		co.lval = false;
		co.type = lco.type;
		return co;
	}

	/**
	 * Generate an instruction sequence that holds the address of the variable in a code object
	 * 
	 * If it's a global variable, just get the address from the symbol table
	 * 
	 * If it's a local variable, compute the address relative to the frame pointer (fp)
	 * 
	 * @param lco The code object holding a variable
	 * @return a list of instructions that puts the address of the variable in a register
	 */
	private InstructionList generateAddrFromVariable(CodeObject lco) {

		InstructionList il = new InstructionList();

		//Step 1:
		SymbolTableEntry symbol = lco.getSTE();
		String address = symbol.addressToString();

		//Step 2:
		Instruction compAddr = null;
		if (symbol.isLocal()) {
			//If local, address is offset
			//need to load fp + offset
			//addi tmp' fp offset
			compAddr = new Addi("fp", address, generateTemp(Scope.Type.INT));
		} else {
			//If global, address in symbol table is the right location
			//la tmp' addr //Register type needs to be an int
			compAddr = new La(generateTemp(Scope.Type.INT), address);
		}
		il.add(compAddr); //add instruction to code object

		return il;
	}

//	private CodeObject generateAddrFromVariable2(InstructionList l) {
//
//		CodeObject co = new CodeObject();
//		for (Instruction in : l){
//			Instruction loadAddr = ;
//			co.code.add(loadAddr); //add instruction to code object
//			co.temp = loadAddr.getDest(); //temp is in destination of la
//		}
//
//		co.lval = true; //co holds an lval, because it's an address
//		co.ste = null; //not a variable
//		co.type = .getType(); //even though register type is an int, address points to Type
//
//		return co;
//	}

//	private CodeObject generateAddrFromVariable2(CodeObject lco) {
//
//		CodeObject co = new CodeObject();

//		//Step 1:
//		SymbolTableEntry symbol = lco.getSTE();
//		String address = String.valueOf(symbol.getAddress());
//
//		//Step 2:
//		//la tmp' addr //Register type needs to be an int
//		Instruction loadAddr = new La(generateTemp(Scope.Type.INT), address);
//
//		co.code.add(loadAddr); //add instruction to code object
//		co.lval = true; //co holds an lval, because it's an address
//		co.temp = loadAddr.getDest(); //temp is in destination of la
//		co.ste = null; //not a variable
//		co.type = symbol.getType(); //even though register type is an int, address points to Type
//		co.code.addAll(generateAddrFromVariable(lco));
//		return co;
//	}


}
