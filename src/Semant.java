import ast.*;

import java.util.ArrayList;
import java.util.List;

/* ------------------------------- High level view of the program -------------------------------
 *
 *
 *  Inheritance graph is first built by calling the ClassTable constructor. After the constructor
 *  call inheritance graph begins to construct storing class nodes and all of their feature
 *  (attributes + methods), along with inheritance connections
 *
 *  ScopeChecker is then called and uses the predefined SymbolTable datastructure to manage scope
 *  The SymbolTable has key ID of type Symbol that points to a Tuple of 4 values, these are
 *  | Return Type (Symbol) | Kind (an enum, METHOD OR VAR) | MethodSignature (nullable, formal types and variable names) | ClassDeclaration (class ID was declared in)
 *  There is no need for forward referencing, as in this implementation as in COOL only features and
 *  classes can be forward referenced, all of this information is stored in the Inheritance graph
 *  ( via the method 'searchFeatures' in InheritanceTree class )
 *  So the general logic for checking this is -> if variable not in SymbolTable AND not in inheritanceGraph
 *  then and only then is it invalid.
 *
 *  After this ScopeChecker pass, and given so semantic errors occurs, we can determine that all
 *  variables are in scope and valid. We will use this information later on
 *
 *  The third phase is now the type checking done by the TypeCheckingVisitor class. It works similarly
 *  to the ScopeChckingVisitor as it also keeps a SymbolTable of ID's but unlike the scope checking phase
 *  this is mainly used to deduce type values from objects and method call (dispatch) return types
 *
*/

class Semant {

    public static ClassTable classTable;
    // return type, type of ID, method signature (formals + retrun) , class declaration
    public static SymbolTable<Tuple<Symbol, Kind, MethodSignature, Symbol>> symtable = new SymbolTable();
    public static Symbol filename = getFileName();

    public static void analyze(ProgramNode program) {
        ArrayList<ClassNode> cls =(ArrayList<ClassNode>) (new ArrayList<>(program.getClasses()).clone());
        classTable = new ClassTable(cls);


//        TODO: maybe go into typechecking instead?
        ScopeCheckingVisitor scopecheckVisitor = new ScopeCheckingVisitor();
        program.accept(scopecheckVisitor, null);

        if (Utilities.errors()) {
            Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);
        }

        TypeCheckingVisitor typecheckVisitor = new TypeCheckingVisitor();
        program.accept(typecheckVisitor, null);

        if (Utilities.errors()) {
            Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);
        }
    }

//    TODO find out how to actually do this
    public static Symbol getFileName() {
        ArrayList<Symbol> syms = new ArrayList<>(StringTable.stringtable.values());
        for (Symbol sym : syms) {
            String s = sym.getName();
            int len = s.length();
            if (len > 3 && s.substring(len-3,len).equals(".cl")) {
                return sym;
            }
        }
        return null;
    }

}
// -------------- Helper Classes -------------- //



/*
    generic java class, similar to a tuple in python
    bit its size is static
    immutable so its hashable,
    used as the value pair for symboltable
*/
final class Tuple<T,V,K,X>{
    final public T first;
    final public V second;
    final public K third;
    final public X fourth;

    Tuple(T first, V second, K third, X fourth) {
        this.first = first;
        this.second = second;
        this.third = third;

        this.fourth = fourth;
    }
}

final class MethodSignature{
    public final List<Symbol> formalTypes;
    public final List<Symbol> formalNames;

    MethodSignature(List<Symbol> formalTypes, List<Symbol> formalNames, Symbol declrartionClass) {
        this.formalTypes = formalTypes;
        this.formalNames = formalNames;
    }
}

enum Kind {
    VAR,
    METHOD,

}

