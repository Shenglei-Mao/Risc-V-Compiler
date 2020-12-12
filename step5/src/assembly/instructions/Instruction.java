package assembly.instructions;

import java.util.HashSet;
import java.util.Set;

/**
 * Superclass for all Instructions. Most fields do not have accessors
 * because they are only used in toString methods used to emit instructions.
 */
public abstract class Instruction {

	/*
	* list of possible op codess
	*/
	public enum OpCode {
        LI("LI"),
        LA("LA"),
		ADD("ADD"),
		SUB("SUB"),
		DIV("DIV"),
		MUL("MUL"),
		NEG("NEG"),
		MV("MV"),
		LW("LW"),
		SW("SW"),
		PUTS("PUTS"),
		PUTI("PUTI"),
		GETI("GETI"),
		HALT("HALT"),
		ADDI("ADDI"),
		/* BRANCH INSTRUCTIONS */
		BEQ("BEQ"),
		BGE("BGE"),
		BGT("BGT"),
		BLE("BLE"),
		BLT("BLT"),
		BNE("BNE"),
		J("J"),
		/* FLOAT INSTRUCTIONS */
		FADDS("FADD.S"),
		FSUBS("FSUB.S"),
		FDIVS("FDIV.S"),
		FMULS("FMUL.S"),
		FMVS("FMV.S"),
		FNEGS("FNEG.S"),
		FLW("FLW"),
		FSW("FSW"),
		GETF("GETF"),
		PUTF("PUTF"),
		FIMMS("FIMM.S"),
		FLT("FLT.S"),
		FLE("FLE.S"),
		FEQ("FEQ.S"),
		/* FUNCTION CALL AND RETURN */
		JR("JR"),
		RET("RET");


		private String opCodeName;
		private OpCode(String name) {
			this.opCodeName = name;
		}

		public String toString() {
			return this.opCodeName;
		}
	}
	
	String src1; //holds src operand, if needed
	String src2; //holds src operand, if needed
	String dest; //holds destination operand, if needed
	String label; //holds other value (immediate, label)
	OpCode oc; //op code
	
	/** 
	 * Default constructor, not used except by implementing class
	 */
	protected Instruction() {
    }

    /**
	 * @return Returns destination of instruction. Useful for code generation
	 */
    public String getDest() {
        return this.dest;
	}

	public String getSrc1() { return this.src1; }

	public String getSrc2() { return this.src2; }
	
	public enum Operand {
		SRC1,
		SRC2,
		DEST
	};

	public OpCode getOC() {
		return oc;
	}

	public boolean isFloatOp() {
		OpCode oc = getOC();
		return (oc == OpCode.FADDS || oc == OpCode.FSUBS || oc == OpCode.FDIVS || oc == OpCode.FMULS
				|| oc == OpCode.FMVS || oc == OpCode.FNEGS || oc == OpCode.FLW || oc == OpCode.FSW
				|| oc == OpCode.GETF || oc == OpCode.PUTF || oc == OpCode.FIMMS || oc == OpCode.FLT
				|| oc == OpCode.FLE || oc == OpCode.FEQ );
	}

	public boolean isIntOp() {
		OpCode oc = getOC();
		return (oc == OpCode.LI || oc == OpCode.LA || oc == OpCode.ADD || oc == OpCode.SUB
				|| oc == OpCode.DIV || oc == OpCode.MUL || oc == OpCode.NEG || oc == OpCode.MV
				|| oc == OpCode.LW || oc == OpCode.SW || oc == OpCode.PUTS || oc == OpCode.PUTI
				|| oc == OpCode.GETI || oc == OpCode.ADDI);
	}

	public boolean isUnconditionalJump() {
		OpCode oc = getOC();
		return oc == OpCode.J;
	}

	public String getOperand(Operand o) {
		switch (o) {
			case SRC1: return src1;
			case SRC2: return src2;
			case DEST: return dest;
			default: throw new Error("Shouldn't get here");
		}
	}

	public String getLabel() {
		return label;
	}

	public boolean is3AC(Operand o) {
		switch (o) {
			case SRC1: return is3AC(src1);
			case SRC2: return is3AC(src2);
			case DEST: return is3AC(dest);
			default: throw new Error("Shouldn't get here");
		}
	}

	static public boolean is3AC(String s) {
		return ((s != null) && (s.charAt(0) == '$'));
	}

	public boolean is3AC() {
		return (is3AC(Operand.SRC1) ||
				is3AC(Operand.SRC2) ||
				is3AC(Operand.DEST));
	}

	public boolean isConditionalBranch() {
		OpCode oc = getOC();
		if (oc == OpCode.BEQ || oc == OpCode.BGE || oc == OpCode.BGT || oc == OpCode.BLE || oc == OpCode.BLT
				|| oc == OpCode.BNE) {
			return true;
		}
		return false;
	}

	private boolean isLocal(String op) {
		return (op != null) && (op.startsWith("$l"));
	}

	private boolean isTemp(String op) {
		return (op != null) && (op.startsWith("$t"));
	}

	private boolean isGlobal(String op) {
		return (op != null) && (op.startsWith("$g"));
	}

	public Set<String> genKillSet() {
		Set<String> result = new HashSet<>();
		if (isLocal(getDest()) || isTemp(getDest()) || isGlobal(getDest())) {
			result.add(getDest());
		}
		return result;
	}

	public Set<String> genGSet() {
		Set<String> result = new HashSet<>();
		if (isLocal(src1) || isTemp(src1) || isGlobal(src1)) {
			result.add(src1);
		}
		if (isLocal(src2) || isTemp(src2) || isGlobal(src2)) {
			result.add(src2);
		}
		return result;
	}
}