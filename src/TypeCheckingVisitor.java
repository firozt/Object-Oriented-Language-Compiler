import ast.*;
import ast.visitor.BaseVisitor;

import java.util.*;

public class TypeCheckingVisitor extends BaseVisitor<Object, Object> {
    static SymbolTable<Tuple<Symbol,Kind,MethodSignature, Symbol>> symtable2 = new SymbolTable<>(); // maps id -> type
    static ClassNode currentClass;
    public TypeCheckingVisitor()  {

        // enter program
        symtable2.enterScope();
    }

    @Override
    public Object visit(ProgramNode node, Object data) {

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

        currentClass = node; // update current class
        symtable2.enterScope(); // make new scope
        Semant.loadInheritedClassScopes(node, symtable2); // loads inherited class features
        Object res = super.visit(node, data); // visits all features
        symtable2.exitScope();
        return ret;
    }

    @Override
    public Object visit(AttributeNode node, Object data) {
        // attribute has an optional init that we need to check
        // add type to second symtable

        // attribute name cannot be self
        if (node.getName().equals(TreeConstants.self)) {
            Utilities.semantError(Semant.filename, node)
                    .println("'self' cannot be the name of an attribute.");
            return ret;
        }
//        TODO unsure if we should return early in an invalid var name

        // checks if attribute is already defined which is invalid (checks inherited class also)
        if (symtable2.lookup(node.getName())!=null) {
            Utilities.semantError(Semant.filename,node)
                    .println("Attribute "+node.getName().getName()+" is an attribute of an inherited class.");
        }

        // add id to symtable
        symtable2.addId(
                node.getName(),
                new Tuple<>(
                        node.getType_decl(),
                        Kind.VAR,
                        null,
                        currentClass.getName()
                )
        );



        // find type of expr and compare it to declaration
        visit(node.getInit(),data);
        Symbol exprType = node.getInit().getType();

        if (exprType.equals(TreeConstants.No_type)) {
            return ret; // no expr which is valid
        }

        if (!Semant.classTable.tree.isSubType(node.getType_decl(), exprType)) {
            Utilities.semantError(Semant.filename,node)
                    .println(
                            "Inferred type "+node.getType_decl()+" of initialization of attribute "+node.getInit().getType()+" does not conform to declared type "+node.getType_decl()+"."
                    );
        }
        return ret;
    }

    @Override
    public Object visit(AssignNode node, Object data) {
        // assign node
        // check expr type == symboltable type

        visit(node.getExpr(),data);
        Symbol nodeType = symtable2.lookup(node.getName()).first;
        if (!Semant.classTable.tree.isSubType(node.getExpr().getType(),nodeType)) {
            Utilities.semantError(Semant.filename,node)
                    .println("Type "+ node.getExpr().getType().getName() +" of assigned expression does not conform to declared type "+nodeType.getName()+" of identifier "+node.getName().getName()+".");
            node.setType(TreeConstants.Object_);
            return ret;
        }
        node.setType(nodeType);

        return ret;
    }

    @Override
    public Object visit(NoExpressionNode node, Object data) {
        node.setType(TreeConstants.No_type);
        return ret;
    }

    @Override
    public Object visit(StringConstNode node, Object data) {
        // string constant
        node.setType(TreeConstants.Str);
//        return super.visit(node, data);
        return ret;
    }

    @Override
    public Object visit(BoolConstNode node, Object data) {
        // bool constant
        node.setType(TreeConstants.Bool);
//        return super.visit(node, data);
        return ret;
    }

    @Override
    public Object visit(IntConstNode node, Object data) {
        // int constant
        node.setType(TreeConstants.Int);
//        return super.visit(node, data);
        return ret;
    }


    @Override
    public Object visit(ObjectNode node, Object data) {
        System.out.println("IN " +node.getName().getName());

        // variable can be of any type
        // we need to check Stringtable to find out type
        Tuple<Symbol, Kind, MethodSignature, Symbol> symtableData = symtable2.lookup(node.getName());

        // edge case of self keyword
        if (node.getName().equals(TreeConstants.self)) {
            node.setType(TreeConstants.SELF_TYPE);
        } else {
            node.setType(symtableData.first);

        }

        return ret;
    }


    @Override
    public Object visit(LetNode node, Object data) {

        // let node
        // we need to check that the expr (optional) type is the same type as decl
        // type of let node should always be its type declr
        node.setType(node.getType_decl());


        // add var to symtable2

        // get expr type
        Object init = visit(node.getInit(),data);
        symtable2.enterScope();
        symtable2.addId(
                node.getIdentifier(),
                new Tuple<>(
                        node.getType_decl(),
                        Kind.VAR,
                        null,
                        currentClass.getName()
                )

        );
        Object body = visit(node.getBody(), data);
        symtable2.exitScope();
        // checks init is the same type as declr
        if (!Semant.classTable.tree.isSubType(node.getInit().getType(), node.getType_decl())) {
            Utilities.semantError(Semant.filename, node)
                    .println(
                            "Inferred type "+node.getInit().getType().getName()+" of initialization of "+node.getIdentifier().getName()+" does not conform to identifier's declared type "+node.getType_decl().getName()+"."
                    );
        }
        // TODO: may be wrong
        node.setType(node.getType_decl());
        return ret;
    }

    //    ---------------------- UNARY OPERATIONS ----------------------    //
    /*
     * all of these nodes take one value
     */

    @Override
    public Object visit(IsVoidNode node, Object data) {
        // isvoid operator
        // takes any type
        // return true if var is void else false
        // we only need to put the return type, nothing else
        node.setType(TreeConstants.Bool);
        return super.visit(node, data);
    }

    @Override
    public Object visit(CompNode node, Object data) {
        // '!' operator
        // only takes in bool, returns bool
        node.setType(TreeConstants.Bool);
        visit(node.getE1(),data);
        if (node.getE1().getType() != TreeConstants.Bool) {
            Utilities.semantError(Semant.filename,node)
                    .println("Argument of 'not' has type "+node.getE1().getType()+" instead of Bool");
        }

        System.out.println("IN COMPNODE");
        return ret;
    }

    public Object visit(NegNode node, Object data) {
        // negation node
        // can only take in ints, returns int

        visit(node.getE1(),data);
        if (!node.getE1().getType().equals(TreeConstants.Int)) {
            Utilities.semantError(Semant.filename, node)
                    .println("Argument of '~' has type "+node.getE1().getType().getName()+" of Int.");
        }

        node.setType(TreeConstants.Int);
        return ret;
    }

    @Override
    public Object visit(NewNode node, Object data) {
        node.setType(node.getType_name());
        return ret;
    }


    //    ---------------------- COMPARISON OPERATIONS ----------------------    //
    /*
     * all of these nodes take two values, and return a bool
     * valid types for e1 and e2 are ints with an exception
     * in the eq node, that can take string,int and bool
     */
    @Override
    public Object visit(LTNode node, Object data) {
//        less than
//        can only be done on ints, returns bool
        Object res = visit(node.getE1(),data);
        Object res2 = visit(node.getE2(),data);

        // check that they are both of type int

        if(
                !node.getE1().getType().getName().equals(TreeConstants.Int.getName()) ||
                !node.getE2().getType().getName().equals(TreeConstants.Int.getName())
        ) {
            Utilities.semantError(Semant.filename, node)
                    .println("Illegal comparison with a basic type");
        }

        return res;
    }

    @Override
    public Object visit(LEqNode node, Object data) {
//        less than or equal to
//        can only be done on ints, returns bool

        return super.visit(node, data);
    }

    @Override
    public Object visit(EqNode node, Object data) {
        // equality operator '='
        // has two expr children, visit each
        // any two types can be compared except for if
        // type is int, str, bool, they must both be same type
        Set<Symbol>  mustBeSame = Set.of(
                TreeConstants.Int,
                TreeConstants.Str,
                TreeConstants.Bool
        );


        visit(node.getE1(), data);
        visit(node.getE2(), data);

        if (
                ( mustBeSame.contains(node.getE2().getType()) || mustBeSame.contains(node.getE1().getType()) ) &&
                ( node.getE1().getType() != node.getE2().getType() )

        ) {
            Utilities.semantError(Semant.filename,node)
                    .println("Illegal comparison with a basic type");
        }


//        if (
//                !node.getE1().getType().equals(node.getE2().getType()) ||
//        ) {
//            Utilities.semantError(Semant.filename,node)
//                    .println("Illegal comparison with a basic type");
//        }
        node.setType(TreeConstants.Bool);
        return ret;
    }

//    ---------------------- ARITH OPERATIONS ----------------------    //
    /*
    *  All of these operations are only defined for ints
    *  Each one is also a binary operation
    *  I will check if the expr1 and expr2 evals to int
    *  with the use of the helper below
    */

    public void BINOPHelper(BinopNode node, Object data) {
        visit(node.getE1(),data);
        visit(node.getE2(),data);
        Symbol E1Type = node.getE1().getType();
        Symbol E2Type = node.getE2().getType();
        if (!E1Type.equals(TreeConstants.Int) || !E2Type.equals(TreeConstants.Int)) {
            // one or both of E1 or E2 is not an int
            Utilities.semantError(Semant.filename,node)
                    .println("non-Int arguments: "+E1Type.getName()+" + "+E2Type.getName());
        }
        node.setType(TreeConstants.Int);
    }

    @Override
    public Object visit(SubNode node, Object data) {
        BINOPHelper(node, data);
        return super.visit(node, data);
    }

    @Override
    public Object visit(MulNode node, Object data) {
        BINOPHelper(node, data);

        return super.visit(node, data);
    }

    @Override
    public Object visit(PlusNode node, Object data) {
        BINOPHelper(node, data);

        return super.visit(node, data);
    }

    @Override
    public Object visit(DivideNode node, Object data) {
        BINOPHelper(node, data);

        return super.visit(node, data);
    }

    //    --------------------- MISC ----------------------   //

    @Override
    public Object visit(BlockNode node, Object data) {
        // block node
        // returns the type of the last expr

        // visit all exprs
        for (ExpressionNode e : node.getExprs()) {
            visit(e, data);
        }
        // set type
        node.setType(node.getExprs().get(node.getExprs().size()-1).getType());


        return ret;
    }



    @Override
    public Object visit(DispatchNode node, Object data) {
        // method call
        // check arguments passed in match arguments of declration
        // if all is good, set the return type to the return type of the method declaration
        // else set it as object

        node.setType(TreeConstants.Object_); // will get overwritten if no semantic errors occur

        Tuple<Symbol, Kind, MethodSignature, Symbol> symtableData = symtable2.lookup(node.getName());

        if (symtableData == null && Semant.classTable.searchFeatures(currentClass, node.getName())) {
            // default method
//            TODO fix this, by adding default methods to the symtable on program visit
            node.setType(TreeConstants.Object_);
            return ret;
        }

        if (symtableData == null) {
            Utilities.semantError(Semant.filename, node)
                    .println("Dispatch to undefined method "+node.getName().getName()+".");
            return ret;
        }



        List<Symbol> argTypes = symtableData.third.formalTypes;
        List<Symbol> actualsTypes = new ArrayList<>();
        List<Symbol> actualNames = symtableData.third.formalNames;
        // get types of all exprs
        for (ExpressionNode e : node.getActuals()) {
            visit(e, data);
            actualsTypes.add(e.getType());
        }
        // check each type is correct

        if (argTypes.size() != actualsTypes.size()) {
            Utilities.semantError(Semant.filename,node)
                    .println("Method foo called with wrong number of arguments.");

            return ret;
        }

        for(int i = 0; i < argTypes.size(); i++) {
            if (!argTypes.get(i).equals(actualsTypes.get(i))) {
                Utilities.semantError(Semant.filename,node)
                        .println("In call of method "+node.getName().getName()+", type "+actualsTypes.get(i).getName()+" of parameter "+actualNames.get(i)+" does not conform to declared type "+argTypes.get(i).getName()+".");
            }
        }


        node.setType(symtableData.first);
        return ret;
    }

    //    ---------------------- END ----------------------    //


    @Override
    public Object visit(MethodNode node, Object data) {
        // method
        // we have to check that the return type of the expr is a valid type of the methods return
        // we also need to add method signature to symbol table

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
        symtable2
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

        // enter new scope to visit method
        symtable2.enterScope();

        // add formals to scope

        for (int i = 0; i < formalsType.size(); i++) {
            symtable2.addId(
                    formalsName.get(i),
                    new Tuple<>(
                            formalsType.get(i),
                            Kind.VAR,
                            null,
                            currentClass.getName()
                    )
                    );
        }
        // visit method
        visit(node.getExpr(),data);
        // exit scope
        symtable2.exitScope();

        // i didnt implement the visit for an expression node
        if (node.getExpr().getType() == null) {
            System.out.println("Did not handle expr case of " +node.getExpr().toString()+ ". (might wanna check that out)");

        }

        // type check method body
        if (!Semant.classTable.tree.isSubType(node.getExpr().getType(),node.getReturn_type())) {
            Utilities.semantError(Semant.filename,node)
                    .println(
                            "Inferred return type "+node.getExpr().getType().getName()+" of method "+node.getName().getName()+" does not conform to declared return type "+node.getReturn_type().getName()+"."
                    );
        }

        return ret;
//        return super.visit(node, data);
    }
}
