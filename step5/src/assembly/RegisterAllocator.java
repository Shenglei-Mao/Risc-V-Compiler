package assembly;

import assembly.instructions.*;
import compiler.Scope;

import java.util.*;

import static assembly.instructions.Instruction.OpCode.LI;

public class RegisterAllocator {
    /*
    Machine Specifications:
    Integer registers x0 through x31 with the execption of x1 (the return address, also called ra),
    x2 (the stack pointer, also called sp) and x8 (the frame pointer, also called fp). x0 is also reserved
    Note that x0 is hardwired to 0, so you cannot use it.

    Floating point registers f0 through f31.

    x3 is reserved in out policy for loading address
     */
    static String INT_PREFIX = "x";
    static String FLOAT_PREFIX = "f";
    static String GLOBAL = "$g";
    static String LOCAL = "$l";
    static String TEMP = "$t";
    static String ZERO_OFFSET = "0";
    static String SP = "sp";

    int numRegister;
    Scope localScope;
    Scope globalScope;
    TreeMap<String, Record> intRegisterStore = new TreeMap<>((s1, s2) -> s1.length() == s2.length() ? s1.compareTo(s2) : s1.length() - s2.length());

    TreeMap<String, Record> floatRegisterStore = new TreeMap<>((s1, s2) -> s1.length() == s2.length() ? s1.compareTo(s2) : s1.length() - s2.length());
    //Special Register for payload of address
    String X3 = "x3";
    //TODO Optimization
    Record x3Record = new Record();

    public RegisterAllocator(int numRegister, Scope localScope, Scope globalScope) {
        this.numRegister = numRegister;
        this.localScope = localScope;
        this.globalScope = globalScope;
        initRegisterStore();
    }

    private void initRegisterStore() {
        Set<Integer> blackList = new HashSet<>();
        blackList.add(0);
        blackList.add(1);
        blackList.add(2);
        blackList.add(3);
        blackList.add(8);
        for (int i = 0; i < this.numRegister; i++) {
            if (!blackList.contains(i)) {
                intRegisterStore.put("x" + i, new Record());
            }
        }
        for (int i = 0; i < this.numRegister; i++) {
            floatRegisterStore.put("f" + i, new Record());
        }
    }

    class Record {
        boolean dirty;
        String varName;

        public Record() {
            this.dirty = false;
            this.varName = null;
        }

        public Record(boolean dirty, String varName) {
            this.dirty = dirty;
            this.varName = varName;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public void setVarName(String varName) {
            this.varName = varName;
        }

        public boolean isDirty() {
            return dirty;
        }

        public String getVarName() {
            return varName;
        }
    }

    /***
     * Perform liveness analysis, register allocation and convert 3AC to asm
     * @param instructions: instructions in basic block in 3AC
     * @return instructions in asm
     */
    //TODO macro expansion for each 3AC
    public List<Instruction> codeGen(Instruction[] instructions, Set<String> usedIntRegisters, Set<String> usedFloatRegisters) {
        List<Instruction> result = new LinkedList<>();
        List<Set<String>> lives = livenessAnalysis(instructions);
        //TODO Register Allocation Here
        /*
        • Perform code generation and register allocation at the same time
            • Find registers for operands when translating 3AC to assembly
        • Greedily reuse registers
            • Keep operands in registers if operand is live (see lecture on liveness)
            • If operand is already in register, no need for new loads
        • Only store registers back to the stack if necessary
            • Need register for something else (spill register to stack/global memory)
            • At the end of basic block
         */


        /*
            For each tuple op A B C in a BB, do
            Rx = ensure(A)
            Ry = ensure(B)
            if A dead after this tuple, free(Rx)
            if B dead after this tuple, free(Ry)
            Rz = allocate(C) //could use Rx or Ry
            generate code for op
            mark Rz dirty
            At end of BB, for each dirty register
            holding a live value, generate code to store
            register into appropriate variable
         */
        //3AC -> ASM
        //ADD SUB
        //LI
        //FADD FSUB....
        //...
        //ASM

        String src1 = null;
        String src2 = null;
        String dest = null;
        String rx = null;
        String ry = null;
        String rz = null;
        boolean containJump = false;
        boolean containConditionalBranch = false;
        Instruction branchInstr = null;
        Instruction retInstr = null;

        for (int i = 0; i < instructions.length; i++) {
            Instruction instruction = instructions[i];
            //TODO, hope it not break prev code
            if (!instruction.is3AC() && !instruction.isConditionalBranch() && !instruction.isUnconditionalJump() && instruction.getOC() != null) {
                result.add(instruction);
                continue;
            }
            Set<String> curLive = lives.get(i);
            src1 = instruction.getSrc1();
            src2 = instruction.getSrc2();
            dest = instruction.getDest();
            rx = null;
            ry = null;
            rz = null;
            if (instruction.isIntOp()) {
                Set<String> blackList = new HashSet<>();
                switch (instruction.getOC()) {
                    case ADD:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, intRegisterStore, result, curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new Add(rx, ry, rz));
                        //Mark Rz as dirty
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case SUB:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, intRegisterStore, result, curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new Sub(rx, ry, rz));
                        //Mark Rz as dirty
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case MUL:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, intRegisterStore, result, curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new Mul(rx, ry, rz));
                        //Mark Rz as dirty
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case DIV:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, intRegisterStore, result, curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        //generate code for add
                        blackList.add(rz);
                        if (ry.equals(X3)) { //@Michael: TODO this if statement is a "ad-hoc" code (fix) to pass control-flow/test5 with not enough registers
                                                //TODO caused reason: put need address, while other op need value
                                                //TODO fix sugguestion: propgate this part of code to rx, ry and for all instructions like ADD, SUB, FADD...
                                                //TODO do not repeat your self! This part of code sucks.
                            ry = allocate("x3", 0, false, result, blackList, curLive);
                            result.add(new Lw(ry, X3, "0"));
                        }
                        result.add(new Div(rx, ry, rz));
                        //Mark Rz as dirty
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case NEG:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        //generate code for op
                        result.add(new Neg(rx, rz));
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case LI:
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        result.add(new Li(rz, instruction.getLabel()));
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case MV:

                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        if ("$l8".equals(dest)) {
                            retInstr = new Sw(rx, "fp", "8");
                        } else {
                            rz = allocate(dest, 0, true, result, blackList, curLive);
                            //generate code for op
                            result.add(new Mv(rx, rz));//changed ry to rz
                            intRegisterStore.get(rz).setDirty(true);
                        }
                        break;
                    case LW:
//                        rx = ensure(src1, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        blackList.add(rx);
//                        ry = ensure(src2, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        if (!curLive.contains(src1)) {
//                            free(rx, intRegisterStore, result, curLive);
//                        }
//                        rz = ensure(dest, instruction.isIntOp(),
//                            instruction.isFloatOp(), result, blackList, curLive);
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        result.add(new Lw(rz, src1, instruction.getLabel()));
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case LA:
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        result.add(new La(rz, instruction.getLabel()));
                        break;
                    case SW:
//                        rx = ensure(src1, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        blackList.add(rx);
//                        ry = ensure(src2, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        if (!curLive.contains(src1)) {
//                            free(rx, intRegisterStore, result, curLive);
//                        }
//                        if (!curLive.contains(src2)) {
//                            free(ry, intRegisterStore, result, curLive);
//                        }
//                        blackList.add(ry);
                        rz = ensure(dest, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        result.add(new Sw(rz, src1, instruction.getLabel()));
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                    case PUTI:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        //rz = allocate(dest, 0, true, result, blackList);
                        //generate code for op
                        result.add(new PutI(rx));
                        //intRegisterStore.get(rz).setDirty(true);
                        break;
                    case GETI:
//                        rx = ensure(src1, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        blackList.add(rx);
//                        if (!curLive.contains(src1)) {
//                            free(rx, intRegisterStore, result, curLive);
//                        }
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        //generate code for op
                        result.add(new GetI(rz));
                        break;
                    case PUTS:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        //rz = allocate(dest, 0, true, result, blackList);
                        //generate code for op
                        result.add(new PutS(rx));
                        break;
                    case ADDI:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, intRegisterStore, result, curLive);
                        }
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new Addi(rx, rz, instruction.getLabel()));
                        //Mark Rz as dirty
                        intRegisterStore.get(rz).setDirty(true);
                        break;
                }
                if (rx != null) {
                    usedIntRegisters.add(rx);
                }
                if (ry != null) {
                    usedIntRegisters.add(ry);
                }
                if (rz != null) {
                    usedIntRegisters.add(rz);
                }
            } else if (instruction.isFloatOp()) {
                Set<String> blackList = new HashSet<>();
                switch (instruction.getOC()) {
                    case FADDS:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, floatRegisterStore, result, curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new FAdd(rx, ry, rz));
                        //Mark Rz as dirty
                        floatRegisterStore.get(rz).setDirty(true);
                        break;
                    case FSUBS:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, floatRegisterStore, result, curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new FSub(rx, ry, rz));
                        //Mark Rz as dirty
                        floatRegisterStore.get(rz).setDirty(true);
                        break;
                    case FMULS:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, floatRegisterStore, result,curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new FMul(rx, ry, rz));
                        //Mark Rz as dirty
                        floatRegisterStore.get(rz).setDirty(true);
                        break;
                    case FDIVS:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        if (!curLive.contains(src2)) {
                            free(ry, floatRegisterStore, result, curLive);
                        }
                        blackList.add(ry);
                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        //generate code for add
                        result.add(new FDiv(rx, ry, rz));
                        //Mark Rz as dirty
                        floatRegisterStore.get(rz).setDirty(true);
                        break;
                    case FNEGS:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        //generate code for op
                        result.add(new FNeg(rx, rz));
                        floatRegisterStore.get(rz).setDirty(true);
                        break;
                    case FIMMS:
                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        result.add(new FImm(rz, instruction.getLabel()));
                        floatRegisterStore.get(rz).setDirty(true);
                        break;
                    case FSW:
//                        rx = ensure(src1, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        blackList.add(rx);
//                        ry = ensure(src2, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        if (!curLive.contains(src1)) {
//                            free(rx, intRegisterStore, result, curLive);
//                        }
//                        if (!curLive.contains(src2)) {
//                            free(ry, intRegisterStore, result, curLive);
//                        }
//                        blackList.add(ry);
//                        result.add(new Fsw(rz, rx, ry));
//                        intRegisterStore.get(rz).setDirty(true);
//                        break;


                        rz = ensure(dest, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (rz.equals(X3)) { //@Michael: TODO this if statement is a "ad-hoc" code (fix) to pass control-flow/test5 with not enough registers
                            //TODO caused reason: put need address, while other op need value
                            //TODO fix sugguestion: propgate this part of code to rx, ry and for all instructions like ADD, SUB, FADD...
                            //TODO do not repeat your self! This part of code sucks.
                            rz = allocate("x3", 1, false, result, blackList, curLive);
                            result.add(new Flw(rz, X3, "0"));
                        }
                        result.add(new Fsw(rz, src1, instruction.getLabel()));
                        if (!rz.startsWith("x")) {
                            floatRegisterStore.get(rz).setDirty(true);
                        }
                        break;
                    case FLW:
//                        rx = ensure(src1, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        blackList.add(rx);
//                        ry = ensure(src2, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        if (!curLive.contains(src1)) {
//                            free(rx, floatRegisterStore, result, curLive);
//                        }
//                        result.add(new Flw(rz, rx, instruction.getLabel()));//not sure?
//                        floatRegisterStore.get(rz).setDirty(true);
//                        break;

                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        result.add(new Flw(rz, src1, instruction.getLabel()));
                        floatRegisterStore.get(rz).setDirty(true);
                        break;
                    case PUTF:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        //rz = allocate(dest, 0, true, result, blackList);
                        //generate code for op
                        if (rx.equals(X3)) { //@Michael: TODO this if statement is a "ad-hoc" code (fix) to pass control-flow/test5 with not enough registers
                            //TODO caused reason: put need address, while other op need value
                            //TODO fix sugguestion: propgate this part of code to rx, ry and for all instructions like ADD, SUB, FADD...
                            //TODO do not repeat your self! This part of code sucks.
                            rx = allocate("x3", 1, false, result, blackList, curLive);
                            result.add(new Flw(rx, X3, "0"));
                        }
                        result.add(new PutF(rx));
                        //intRegisterStore.get(rz).setDirty(true);
                        break;
                    case GETF:
//                        rx = ensure(src1, instruction.isIntOp(),
//                                instruction.isFloatOp(), result, blackList, curLive);
//                        blackList.add(rx);
//                        if (!curLive.contains(src1)) {
//                            free(rx, floatRegisterStore, result, curLive);
//                        }
                        //rz = allocate(dest, 0, true, result, blackList);
                        //generate code for op

                        rz = allocate(dest, 1, true, result, blackList, curLive);
                        //generate code for op
                        result.add(new GetF(rz));
                        break;
                    case FMVS:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        if ("$l8".equals(dest)) {
                            retInstr = new Fsw(rx, "fp", "8");
                        } else {
                            rz = allocate(dest, 1, true, result, blackList, curLive);
                            //generate code for op
                            result.add(new FMv(rx, rz));//changed ry to rz
                            floatRegisterStore.get(rz).setDirty(true);
                        }
                        break;
                    case FEQ:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        result.add(new Feq(rx, ry, rz));
                        floatRegisterStore.get(rx).setDirty(true);
                        break;
                    case FLE:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        result.add(new Fle(rx, ry, rz));
                        floatRegisterStore.get(rx).setDirty(true);
                        break;
                    case FLT:
                        rx = ensure(src1, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        blackList.add(rx);
                        ry = ensure(src2, instruction.isIntOp(),
                                instruction.isFloatOp(), result, blackList, curLive);
                        if (!curLive.contains(src1)) {
                            free(rx, floatRegisterStore, result, curLive);
                        }
                        rz = allocate(dest, 0, true, result, blackList, curLive);
                        result.add(new Flt(rx, ry, rz));
                        floatRegisterStore.get(rx).setDirty(true);
                        break;
                }
                if (rx != null) {
                    if (rx.startsWith("x")) {
                        usedIntRegisters.add(rx);
                    } else {
                        usedFloatRegisters.add(rx);
                    }
                }
                if (ry != null) {
                    if (ry.startsWith("x")) {
                        usedIntRegisters.add(ry);
                    } else {
                        usedFloatRegisters.add(ry);
                    }
                }
                if (rz != null) {
                    if (rz.startsWith("x")) {
                        usedIntRegisters.add(rz);
                    } else {
                        usedFloatRegisters.add(rz);
                    }
                }
            } else if (instruction.isUnconditionalJump()){
                containJump = true;
            } else if (instruction.isConditionalBranch()) {
                containConditionalBranch = true;
                Set<String> blackList = new HashSet<>();
                if (!src1.startsWith("x")) {
                    rx = ensure(src1, true,
                            false, result, blackList, curLive);
                    blackList.add(rx);
                } else {
                    rx = src1;
                }
                if (!src2.startsWith("x")) {
                    ry = ensure(src2, true,
                           false, result, blackList, curLive);
                    blackList.add(ry);
                } else {
                    ry = src2;
                }

                if (!curLive.contains(src1)) {
                    free(rx, intRegisterStore, result, curLive);
                }
                if (!curLive.contains(src2)) {
                    free(ry, intRegisterStore, result, curLive);
                }

                if (rx.equals(X3)) { //@Michael: TODO this if statement is a "ad-hoc" code (fix) to pass control-flow/test5 with not enough registers
                    //TODO caused reason: put need address, while other op need value
                    //TODO fix sugguestion: propgate this part of code to rx, ry and for all instructions like ADD, SUB, FADD...
                    //TODO do not repeat your self! This part of code sucks.
                    rx = allocate("x3", 0, false, result, blackList, curLive);
                    result.add(new Lw(rx, X3, "0"));
                }

                if (ry.equals(X3)) { //@Michael: TODO this if statement is a "ad-hoc" code (fix) to pass control-flow/test5 with not enough registers
                    //TODO caused reason: put need address, while other op need value
                    //TODO fix sugguestion: propgate this part of code to rx, ry and for all instructions like ADD, SUB, FADD...
                    //TODO do not repeat your self! This part of code sucks.
                    ry = allocate("x3", 0, false, result, blackList, curLive);
                    result.add(new Lw(ry, X3, "0"));
                }

                switch (instruction.getOC()) {
                    case BEQ:
                        branchInstr = new Beq(rx, ry, instruction.getLabel());
                        break;
                    case BNE:
                        branchInstr = new Bne(rx, ry, instruction.getLabel());
                        break;
                    case BLE:
                        branchInstr = new Ble(rx, ry, instruction.getLabel());
                        break;
                    case BLT:
                        branchInstr = new Blt(rx, ry, instruction.getLabel());
                        break;
                    case BGE:
                        branchInstr = new Bge(rx, ry, instruction.getLabel());
                        break;
                    case BGT:
                        branchInstr = new Bgt(rx, ry, instruction.getLabel());
                        break;
                }
            } else {
                //is label
                result.add(instruction);
            }
        }
        result.add(new Blank("Saving registers at end of BB"));
        //TODO, at the end of basic block, flush of all live register is required, mechanically, free all live registers
        Iterator iterator = intRegisterStore.keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            free(key, intRegisterStore, result, lives.get(lives.size() - 1));
        }
        iterator = floatRegisterStore.keySet().iterator();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            free(key, floatRegisterStore, result, lives.get(lives.size() - 1));
        }

        //Put result into fp + 8
        if (retInstr != null) {
            result.add(retInstr);
        }
        if (containConditionalBranch) {
            result.add(branchInstr);
        }
        if (containJump) {
            Instruction instruction = instructions[instructions.length - 1];
            result.add(new J(instruction.getLabel()));
        }

        return result;
    }

    /***
     * valid inputs: only temp, global and local
     * @param variable
     * @param isInt
     * @param isFloat
     * @param result
     * @param blackRegister
     * @return
     * Mote if global, return fixed address x3
     */
    private String ensure(String variable, boolean isInt, boolean isFloat, List<Instruction> result, Set<String> blackRegister, Set<String> curLive) {
        assert (variable.startsWith(LOCAL) || variable.startsWith(GLOBAL) || variable.startsWith(TEMP));
        /*
            ensure(opr)
            if opr is already in register R
            return R
            else
            R = allocate(opr)
            generate load from opr into R
            return R
        */

        if (isInt) {
            for (Map.Entry<String, Record> entry : intRegisterStore.entrySet()) {
                String registerName = entry.getKey();
                Record record = entry.getValue();
                if (record.getVarName() != null && record.getVarName().equals(variable)) {
                    return registerName;
                }
            }
            if (variable.startsWith(LOCAL)) {
                String register = allocate(variable, 0, false, result, blackRegister, curLive);
                String offset = variable.substring(2);
                result.add(new Lw(register, "fp", offset));
                return register;
            }

            if (variable.startsWith(TEMP)) {
                //src operator is non dirty
                String register = allocate(variable, 0, false, result, blackRegister, curLive);
                //lw r offset(sp)
                result.add(new Lw(register, SP, localScope.getSymbolTableEntry(variable).addressToString()));
                return register;
            } else if (variable.startsWith(GLOBAL)) {
                //La x3, 0x2000000
                //Lw r, (0)x3
                variable = variable.substring(2);
                result.add(new La(X3, globalScope.getSymbolTableEntry(variable).addressToString()));
                //result.add(new Lw(register, X3, "0"));
                return X3;
            }


        } else if (isFloat) {
            for (Map.Entry<String, Record> entry : floatRegisterStore.entrySet()) {
                String registerName = entry.getKey();
                Record record = entry.getValue();
                if (record.getVarName() != null && record.getVarName().equals(variable)) {
                    return registerName;
                }
            }
            if (variable.startsWith(LOCAL)) {
                String register = allocate(variable, 1, false, result, blackRegister, curLive);
                String offset = variable.substring(2);
                result.add(new Flw(register, "fp", offset));
                return register;
            }

            if (variable.startsWith(TEMP)) {
                String register = allocate(variable, 1, false, result, blackRegister, curLive);
                //lw r offset(sp)
                result.add(new Flw(register, SP, localScope.getSymbolTableEntry(variable).addressToString()));
                return register;
            } else if (variable.startsWith(GLOBAL)) {
                //La x3, 0x2000000
                //Lw r, (0)x3
                variable = variable.substring(2);
                result.add(new La(X3, globalScope.getSymbolTableEntry(variable).addressToString()));
                //result.add(new Flw(register, SP, localScope.getSymbolTableEntry(variable).addressToString()));
                return X3;
            }

        } else {
            throw new IllegalArgumentException("RegisterAllocator - Ensure: variable type is not int or float");
        }
        throw new IllegalArgumentException("Should not reach here");
    }

    /***
     * @Michael: Not Round Robin (reference compiler is implemented as round robin however),
     * Out policy, always pick the smallest index
     * prefer non-dirty over dirty, and smaller index
     * O(n) linear scan
     *
     * Note: Allocate here generate 0 asm code!
     * @param variable
     * @param selector
     * @param isDirty
     * @param result
     * @param blackRegister
     * @return
     */
    private String allocate(String variable, int selector, boolean isDirty, List<Instruction> result, Set<String> blackRegister, Set<String> curLive) {
        //selector 0 - int, 1 - float
        String resultRegister = null;
        Map.Entry<String, Record> firstEntry = null;
        Iterator iterator = null;
        TreeMap<String, Record> store = null;
        if (selector == 0) {
            iterator = intRegisterStore.entrySet().iterator();
            store = intRegisterStore;
        } else if (selector == 1) {
            iterator = floatRegisterStore.entrySet().iterator();
            store = floatRegisterStore;
        } else {
            throw new IllegalArgumentException("Register Allocator - allocate: Allocate selector has invalid value");
        }

        while (iterator.hasNext()) {
            Map.Entry<String, Record> entry = (Map.Entry<String, Record>) iterator.next();
            String registerName = entry.getKey();
            Record record = entry.getValue();
            if (record.getVarName() == null) {
                resultRegister = registerName;
                break;
            }
            if (!record.isDirty() && !blackRegister.contains(registerName)) {
                firstEntry = entry;
            }
        }

        //Our Policy, pick the first non-dirty to free
        if ((resultRegister == null) && (firstEntry != null)) {
            free(firstEntry, store, result, curLive);
            store.put(firstEntry.getKey(), new Record(isDirty, variable));
            resultRegister = firstEntry.getKey();
        } else if (resultRegister == null && !blackRegister.contains(store.firstKey())) {
            free(store.firstEntry(), store, result, curLive);
            store.put(store.firstKey(), new Record(isDirty, variable));
            resultRegister = store.firstKey();
        } else if (resultRegister == null && !blackRegister.contains(store.firstKey())) {
            String secondKey = (String) store.keySet().toArray()[1];
            free(store.floorEntry(store.firstKey()), store, result, curLive);
            resultRegister = store.floorKey(store.firstKey());
        } else if (resultRegister == null) {
            String thirdKey = (String) store.keySet().toArray()[2];
            free(thirdKey, store, result, curLive);
            resultRegister = thirdKey;
        }
        assert (result != null);
        store.put(resultRegister, new Record(isDirty, variable));
        return resultRegister;
    }


    private boolean isEntryInt(Map.Entry<String, Record> entry) {
        return entry.getKey().startsWith(INT_PREFIX);
    }

    private boolean isEntryInt(String key) {
        return key.startsWith(INT_PREFIX);
    }


    private void free(String key, Map<String, Record> table, List<Instruction> result, Set<String> lives) {
        if (key.equals("x0")) {
            return;
        }
        if (table.get(key).isDirty() && lives.contains(table.get(key).getVarName())) {
            String varName = table.get(key).getVarName();
            if (varName.startsWith(LOCAL)) {
                //int vs float
                /*
                    $l-4
                 */
                if (isEntryInt(key)) {
                    String offset = varName.substring(2);
                    result.add(new Sw(key, "fp", offset));
                } else {
                    String offset = varName.substring(2);
                    result.add(new Fsw(key, "fp", offset));
                }
            } else if (varName.startsWith(GLOBAL)) {
                /*
                    LA x3 0x2000000
                    Sw r -> x3(0)
                 */
                varName = varName.substring(2);
                result.add(new La(X3, globalScope.getSymbolTableEntry(varName).addressToString()));
                if (isEntryInt(key)) {
                    result.add(new Sw(key, X3, ZERO_OFFSET));
                } else {
                    result.add(new Fsw(key, X3, ZERO_OFFSET));
                }
            } else if (varName.startsWith(TEMP)) {
                /*
                    treat temp as local
                */
                localScope.addSymbol(Scope.Type.INT, varName);
                if (isEntryInt(key)) {
                    result.add(new Sw(key , "fp",
                            localScope.getSymbolTableEntry(varName).addressToString()));
                } else {
                    result.add(new Fsw(key, "fp",
                            localScope.getSymbolTableEntry(varName).addressToString()));
                }
            } else {
                throw new IllegalArgumentException("Register Allocation - Free: Wrong type of variable name");
            }
        } else {
            table.put(key, new Record());
        }
        //TODO hope not break previous working test
        table.put(key, new Record());
    }


    /***
     * @Michael:
     * Note here 3AC to asm store is needed
     * @param entry
     * @param table
     * @param result
     */
    private void free(Map.Entry<String, Record> entry, Map<String, Record> table, List<Instruction> result, Set<String> lives) {
        if (entry.getKey().equals("x0")) {
            return;
        }
        if (entry.getValue().isDirty() && lives.contains(entry.getValue().getVarName())) {
            String varName = entry.getValue().getVarName();
            if (varName.startsWith(LOCAL)) {
                //int vs float
                /*
                    $l-4
                 */
                if (isEntryInt(entry)) {
                    result.add(new Sw(entry.getKey(), "fp",
                            localScope.getSymbolTableEntry(varName).addressToString()));
                } else {
                    result.add(new Fsw(entry.getKey(), "fp",
                            localScope.getSymbolTableEntry(varName).addressToString()));
                }
            } else if (varName.startsWith(GLOBAL)) {
                /*
                    LA x3 0x2000000
                    Sw r -> x3(0)
                 */
                varName = varName.substring(2);
                result.add(new La(X3, globalScope.getSymbolTableEntry(varName).addressToString()));
                if (isEntryInt(entry)) {
                    result.add(new Sw(entry.getKey(), X3, ZERO_OFFSET));
                } else {
                    result.add(new Fsw(entry.getKey(), X3, ZERO_OFFSET));
                }
            } else if (varName.startsWith(TEMP)) {
                /*
                    treat temp as local
                */
                localScope.addSymbol(Scope.Type.INT, varName);
                if (isEntryInt(entry)) {
                    result.add(new Sw(entry.getKey(), "fp",
                            localScope.getSymbolTableEntry(varName).addressToString()));
                } else {
                    result.add(new Fsw(entry.getKey(), "fp",
                            localScope.getSymbolTableEntry(varName).addressToString()));
                }
            } else {
                throw new IllegalArgumentException("Register Allocation - Free: Wrong type of variable name");
            }
        } else {
            table.put(entry.getKey(), new Record());
        }
        //TODO hope not break previous working test
        table.put(entry.getKey(), new Record());
    }


    /***
     * Live Analysis
     * @param instructions 3AC
     * @return live variables at each line of code
     * @Michael: Not sure about global, does global live all the time
     */
    //TODO, if return from function, only all global is live, all local are dead, different from basic block
    private List<Set<String>> livenessAnalysis(Instruction[] instructions) {
        int n = instructions.length;
        List<Set<String>> lives = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lives.add(null);
        }
        for (int i = n - 1; i >= 0; i--) {
            if (i == n - 1) {
                lives.set(i, initLiveSet());
            } else {
                Instruction instruction = instructions[i + 1];
                if (!instruction.is3AC()) {
                    lives.set(i, new HashSet<>(lives.get(i + 1)));
                } else {
                    Set<String> tmp = new HashSet<>(lives.get(i + 1));
                    tmp.removeAll(instruction.genKillSet());
                    tmp.addAll(instruction.genGSet());
                    lives.set(i, tmp);
                }
            }
        }
        return lives;
    }

    private Set<String> initLiveSet() {
        Set<String> result = new HashSet<>();
        for (Scope.SymbolTableEntry entry : globalScope.getEntries()) {
            if (!(entry instanceof Scope.FunctionSymbolTableEntry)) {
                result.add("$g" + entry.getName());
            }
        }
        for (Scope.SymbolTableEntry entry : localScope.getEntries()) {
            if (!(entry instanceof Scope.FunctionSymbolTableEntry)) {
                result.add("$l" + entry.addressToString());
            }
        }
        return result;
    }
}
