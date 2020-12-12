package assembly;

import assembly.exceptions.unchecked.TypeAssertionException;
import ast.*;
import ast.visitor.AbstractASTVisitor;
import compiler.Scope;

import java.util.List;


public class TypeChecker extends AbstractASTVisitor<Scope.Type> {

    static String BinaryNodeException = "Binary Node Type Check Failed";
    static String AssignNodeException = "Assign Node Type Check Failed";
    static String CondNodeException = "Condition Node Type Check Failed";
    static String ReturnNodeException = "Return Node Type Check Failed";
    static String CallNodeNumberException = "Function Call Node Argument Number Mismatched";
    static String CallNodeTypeException = "Function Call Node Type Check Failed";

    @Override
    protected Scope.Type postprocess(VarNode node) {
        return node.getType();
    }

    @Override
    protected Scope.Type postprocess(IntLitNode node) {
        return Scope.Type.INT;
    }

    @Override
    protected Scope.Type postprocess(FloatLitNode node) {
        return Scope.Type.FLOAT;
    }

    @Override
    protected Scope.Type postprocess(BinaryOpNode node, Scope.Type left, Scope.Type right) {
        if (left != right) {
            throw new TypeAssertionException(BinaryNodeException, node);
        }
        return left;
    }

    @Override
    protected Scope.Type postprocess(UnaryOpNode node, Scope.Type expr) {
        return expr;
    }

    @Override
    protected Scope.Type postprocess(AssignNode node, Scope.Type left, Scope.Type right) {
        if (left != right) {
            throw new TypeAssertionException(AssignNodeException, node);
        }
        return left;
    }

    @Override
    protected Scope.Type postprocess(CondNode node, Scope.Type left, Scope.Type right) {
        if (left != right) {
            throw new TypeAssertionException(CondNodeException, node);
        }
        return left;
    }

    @Override
    protected Scope.Type postprocess(ReturnNode node, Scope.Type retExpr) {
        if (node.getFuncSymbol().getType() != retExpr) {
            throw new TypeAssertionException(ReturnNodeException, node);
        }
        return retExpr;
    }

    @Override
    protected Scope.Type postprocess(CallNode node, List<Scope.Type> args) {
        int argsNum = node.getArgs().size();
        int parametersNum = args.size();
        if (argsNum != parametersNum) {
            throw new TypeAssertionException(CallNodeNumberException, node);
        }
        for (int i = 0; i < argsNum; i++) {
            TypedASTNode arg = node.getArgs().get(i);
            if (arg.getType() != args.get(i)) {
                throw new TypeAssertionException(CallNodeTypeException, node);
            }
        }
        return node.getType();
    }
}