import ast.*;
import ast.visitor.BaseVisitor;

import java.util.List;
import java.util.Set;

public class ScopeCheckingVisitor extends BaseVisitor<Object, Object> {
    public static SymbolTable symtable = Semant.symtable; // local ref for less code
    public static ClassNode currentClass = null;

    public ScopeCheckingVisitor() {
    }

// -------------- Creates a new scope -------------- //

    @Override
    public Object visit(ProgramNode node, Object data) { // program
        symtable.enterScope();
        return super.visit(node, data);
    }

    @Override
    public Object visit(ClassNode node, Object data) { // class
        // it is invalid to redefine a default class,
        Set<Symbol> DefaultClassNames = Set.of(
                TreeConstants.Int,
                TreeConstants.Str,
                TreeConstants.Bool,
                TreeConstants.IO,
                TreeConstants.Object_
        );
        if (DefaultClassNames.contains(node.getName())) {
            Utilities.semantError(Semant.filename,node)
                    .println("Redefinition of basic class "+node.getName()+".");
        }


        currentClass = node;
        symtable.enterScope();
        int numPops = Semant.loadInheritedClassScopes(node, symtable); // loads inherited class features

        super.visit(node, data);
        for (int i = 0; i < numPops; i++) { // unloads class and all inherited classes
            symtable.exitScope();
        }
        return ret;
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
        visit(node.getExpr(), data);

        // accessing scope of another class
        // we need to check if method is defined in said class
        if (node.getExpr().getType() != TreeConstants.self) {
            ClassNode methodClass = Semant.classTable.tree.findClass(Semant.classTable.tree.root, node.getExpr().getType());
            boolean existsOnClass = Semant.classTable.featureExistOnClass(node.getExpr().getType(), node.getName()) == null;
            if (!existsOnClass) {
                Utilities.semantError(Semant.filename, node)
                        .println("Dispatch to undefined method "+node.getName().getName()+".");
                return ret;
            }
        }

        if (symtable.lookup(node.getName())==null) {
            Utilities.semantError(Semant.filename, node)
                    .println("Dispatch to undefined method "+node.getName().getName()+".");
            return ret;
        }

        super.visit(node, data);
        return ret;
    }

    @Override
    public Object visit(DispatchNode node, Object data) { // dispatch

        // we need to check if the dispatch is defined in this scope
        visit(node.getExpr(), data);

            // not in scope table and not a valid forward reference
        if (symtable.lookup(node.getName())==null && !Semant.classTable.isValidForwardReference(currentClass, node.getName())) {
            Utilities.semantError(Semant.filename, node)
                    .println("Dispatch to undefined method "+node.getName().getName()+".");
            return ret;
        }

        super.visit(node, data);
        return ret;
    }

    @Override
    public Object visit(CaseNode node, Object data) {
        return super.visit(node, data);
    }

    @Override
    public Object visit(BranchNode node, Object data) {
        Symbol id = node.getName();
        symtable.enterScope();
        symtable.addId(id,new Tuple<>(null,Kind.VAR,null,currentClass));
        visit(node.getExpr(),data);
        symtable.exitScope();

        return ret;
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
        if (node.getName() == TreeConstants.self) { // self is allowed in any context
            return super.visit(node, data);
        }
        // not in scope and not forward referenced
        if (!inScope && !Semant.classTable.searchFeatures(currentClass, node.getName())) {
            Utilities.semantError(Semant.filename, node)
                .println("Undeclared identifier "+ node.getName() +".");
        }


        return super.visit(node, data);
    }
    // -------------- Misc -------------- //


    @Override
    public Object visit(MethodNode node, Object data) {
        // add type to symboltabe, along with formal types
        List<Symbol> formalsType = node // list of signature type
                .getFormals()
                .stream()
                .map(f -> f.getType_decl())
                .toList();

        List<Symbol> formalsName = node // list of signature variable names
                .getFormals()
                .stream()
                .map(f -> f.getName())
                .toList();
        symtable
                .addId(
                        node.getName(),
                        new Tuple<>(
                                node.getReturn_type(),
                                Kind.METHOD,
                                new MethodSignature(
                                        formalsType,
                                        formalsName,
                                        currentClass.getName()
                                ),
                                currentClass.getName()
                        )
                );

        symtable.enterScope();
        // add args to scope

        for (Symbol arg : formalsName) {
            symtable.addId(
                    arg,
                    null // we dont need to keep track of any types
            );
        }

        super.visit(node, data); // go into method body
        symtable.exitScope();
        return  ret;
    }
}



