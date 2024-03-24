import ast.*;
import ast.visitor.BaseVisitor;

import java.util.List;

public class ScopeCheckingVisitor extends BaseVisitor<Object, Object> {
    public static SymbolTable symtable = Semant.symtable; // local ref for less code
    public static ClassNode currentClass = null;

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
    public Object visit(ClassNode node, Object data) { // class
        currentClass = node;
        symtable.enterScope();
        Object res = super.visit(node, data);
        symtable.exitScope();
        return res;
    }

    @Override
    public Object visit(LetNode node, Object data) { // let
        symtable.enterScope();
        symtable.addId(node.getIdentifier(), new Tuple<>(node.getType(),Kind.VAR, null, currentClass.getName()));
        Object ret = super.visit(node, data);
        symtable.exitScope();
        return ret;

    }

    @Override
    public Object visit(StaticDispatchNode node, Object data) { // dispatch
        symtable.enterScope();
        List<ExpressionNode> formals = node.getActuals();


        for (ExpressionNode formal : formals) {
            symtable.addId(formal.getType(),new Tuple<>(null, Kind.VAR, null, currentClass.getName()));
        }
        Object res = super.visit(node, data);
        symtable.exitScope();
        return res;
    }

//    TODO FIX DISPATCH FORMALS
    @Override
    public Object visit(DispatchNode node, Object data) { // dispatch
        symtable.enterScope();
        List<ExpressionNode> formals = node.getActuals();



        for (ExpressionNode formal : formals) {
            symtable.addId(formal.getType(),new Tuple<>(null, Kind.VAR, null, currentClass.getName()));
        }
        Object res = super.visit(node, data);
        symtable.exitScope();
        return res;
    }

    // -------------- Adds ID to SymbolTable -------------- //


    @Override
    public Object visit(AttributeNode node, Object data) { // Attribute
        symtable.addId(node.getName(), new Tuple<>(null, Kind.VAR, null, currentClass.getName()));
        return super.visit(node, data);
    }


    @Override
    public Object visit(AssignNode node, Object data) { // Assign
        symtable.addId(node.getName(),node.getType());
        return super.visit(node, data);
    }

    @Override
    public Object visit(ObjectNode node, Object data) { // Object
        boolean inScope = symtable.lookup(node.getName() ) != null;
        if (node.getName().toString().equals("self")) { // self is allowed in any context
            return super.visit(node, data);
        }

//        System.out.println(
//                Semant.classTable.searchFeatures(currentClass, node.getName())
//        );
        // check global table (inheritance graph), if cant find in scope
        if (!inScope && !Semant.classTable.searchFeatures(currentClass, node.getName())) {


            Symbol filename = (Symbol)StringTable.stringtable.values().toArray()[0];
            Utilities.semantError(filename, node)
                .println("Undeclared identifier "+ node.getName() +".");
        }


        return super.visit(node, data);
    }
    // -------------- Misc -------------- //


}



