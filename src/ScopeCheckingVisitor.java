import ast.*;
import ast.visitor.BaseVisitor;

import java.util.Arrays;
import java.util.List;

public class ScopeCheckingVisitor extends BaseVisitor<Object, Object> {
    public static SymbolTable symtable = Semant.symtable; // local ref for less code

    public ScopeCheckingVisitor() {
    }

// -------------- Creates a new scope -------------- //

    @Override
    public Object visit(ProgramNode node, Object data) { // program
        symtable.enterScope();
        // init with a scope (global scope)
        return super.visit(node, data);
    }
    @Override
    public Object visit(LetNode node, Object data) { // let
        symtable.enterScope();
        symtable.addId(node.getIdentifier(), new Pair<>(node.getType(),Kind.VAR));
        Object ret = super.visit(node, data);
        symtable.exitScope();
        return ret;

    }

    @Override
    public Object visit(StaticDispatchNode node, Object data) { // dispatch
        return super.visit(node, data);
    }

    @Override
    public Object visit(DispatchNode node, Object data) { // dispatch
        symtable.enterScope();
        List<ExpressionNode> formals = node.getActuals();
        for (ExpressionNode formal : formals) {
            symtable.addId(formal.getType(),new Pair<>(null, Kind.VAR));
        }
        Object res = super.visit(node, data);
        symtable.exitScope();
        return res;
    }

    // -------------- Does not create a new scope -------------- //


    @Override
    public Object visit(AttributeNode node, Object data) {
        symtable.addId(node.getName(), new Pair<>(null, Kind.VAR));
        return super.visit(node, data);
    }


    @Override
    public Object visit(AssignNode node, Object data) { // assign
        symtable.addId(node.getName(),node.getType());
        return super.visit(node, data);
    }

    @Override
    public Object visit(ObjectNode node, Object data) { // object
        boolean inScope = symtable.lookup(node.getName() ) != null;
        if (node.getName().toString().equals("self")) { // self is allowed in any context
            return super.visit(node, data);
        }
        if (!inScope) {
            Symbol filename = (Symbol)StringTable.stringtable.values().toArray()[0];
            Utilities.semantError(filename, node)
                .println("Undeclared identifier "+ node.getName() +".");
        }


        return super.visit(node, data);
    }

}



