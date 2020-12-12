package ast;

import ast.visitor.ASTVisitor;
import compiler.Scope;

//TODO Check
public class CastNode extends PtrDerefNode {

    //private ExpressionNode expr;

    public CastNode(Scope.Type t, ExpressionNode expr){
        super(expr);
        // this.expr = expr;
        // this.type = new Scope.Type(Scope.InnerType.PTR); //todo not sure? PTR or void?
        this.setType(t);
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visit(this);
    }

//    public ExpressionNode getExpr() {
//        return expr;
//    }

}
