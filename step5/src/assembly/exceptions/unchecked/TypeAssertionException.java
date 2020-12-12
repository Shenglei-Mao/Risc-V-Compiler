package assembly.exceptions.unchecked;

import ast.ASTNode;


public class TypeAssertionException extends RuntimeException {
    static String AT = "at";

    public TypeAssertionException(String message, ASTNode node) {
        super(message + AT + node.toString());
    }

}