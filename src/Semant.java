import ast.*;
import com.sun.tools.javac.Main;

import java.util.*;

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
 *  to the ScopeChckingVis §itor as it also keeps a SymbolTable of ID's but unlike the scope checking phase
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

        if (Utilities.errors()) Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);

        checkForMain(program.getClasses(), classTable.tree, program);

        ScopeCheckingVisitor scopecheckVisitor = new ScopeCheckingVisitor();
        program.accept(scopecheckVisitor, null);

        if (Utilities.errors()) Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);

        TypeCheckingVisitor typecheckVisitor = new TypeCheckingVisitor();
        program.accept(typecheckVisitor, null);

        if (Utilities.errors()) Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);
    }

//    TODO find out how to actually do this
    public static Symbol getFileName() {
        ArrayList<Symbol> syms = new ArrayList<>(StringTable.stringtable.values());
        for (Symbol sym : syms) {
            String s = sym.getName();
            int len = s.length();
            if (len > 3 && (s.substring(len-3,len).equals(".cl"))) {
                return sym;
            } if (len > 5 &&  s.substring(len-5,len).equals(".test")) {
                return sym;
            }
        }
        return null;
    }

    public static void checkForMain(List<ClassNode> cls, InheritanceTree tree, ProgramNode root) {
        // check for main class
        ClassNode MainClass = null;
        for (ClassNode c : cls) {
            if (c.getName() == TreeConstants.Main) {
                MainClass = c;
            }
        }
        if (MainClass == null) {
            Utilities.semantError(Semant.filename, root)
                    .println("Class Main is not defined");
            return;
        }

        List<MethodNode> methods = new ArrayList<>();
        classTable.tree.seperateFeatures(MainClass.getFeatures(),methods, new ArrayList<>());
        // methods now filled
        MethodNode main = null;
        for (MethodNode m : methods) {
            if (m.getName() == TreeConstants.main_meth) {
                main = m;
            }
        }
        if (main == null) {
            Utilities.semantError(Semant.filename, root)
                    .println("No 'main' method in class Main");
        }

    }

    /*
        Loads the symboltable with features from its inherited classes
        uses the inheritance tree to find scope of visibilty, then
        adds all methods and features that each class contains
        returns number of scopes created
     */
    public static int loadInheritedClassScopes(ClassNode classNode, SymbolTable sym) {
        Stack<InheritanceTreeNode> path = new Stack<>();
        classTable.tree.getClassVisiblity(classTable.tree.root,classNode, path);
        path.pop(); // remove current class

        Collections.reverse(path); // start building from top of tree to bottom
        int res = path.size();
        while(!path.isEmpty()) {
            ClassNode cur = path.pop();

            List<MethodNode> methods = new ArrayList<>();
            List<AttributeNode> attrs = new ArrayList<>();
            classTable.tree.seperateFeatures(cur.getFeatures(),methods,attrs);
            for(MethodNode m : methods) {
                List<Symbol> formalsType = m // list of signature type
                        .getFormals()
                        .stream()
                        .map(f -> f.getType_decl())
                        .toList();
                List<Symbol> formalsName = m // list of signature variable names
                        .getFormals()
                        .stream()
                        .map(f -> f.getName())
                        .toList();
                sym.addId(
                        m.getName(),
                        new Tuple<>(
                                m.getReturn_type(),
                                Kind.METHOD,
                                new MethodSignature(
                                        formalsType,
                                        formalsName,
                                        cur.getName()
                                ), cur.getName()

                        )
                        );
            }
            for (AttributeNode a : attrs) {
                sym.addId(a.getName(),
                        new Tuple<>(
                                a.getType_decl(),
                                Kind.VAR,
                                null,
                                cur.getName()
                        )
                );
            }
            // all features of this class have been added,
            // enter a new scope for the next class
            sym.enterScope();
        }
        return res+1;
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

