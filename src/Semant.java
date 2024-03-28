import ast.*;

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
 *  variables are in scope and valid. While typechecking this means we do not need to check for
 *  object definitions, we can safely assume that all variables and features are well scoped
 *
 *  The third phase is now the type checking done by the TypeCheckingVisitor class. It works similarly
 *  to the ScopeCheckingVisitor as it also keeps a SymbolTable of ID's but unlike the scope checking phase
 *  this is mainly used to deduce type values from objects and method call (dispatch) return types
 *
 *
 *  SELF_TYPE handing:
 *
 *  I only have a naive way of handling SELF_TYPE, in my implementation I have a method on the InheritanceTree class
 *  that checks if type1 is a subtype of type2, in this method if one of the types is detected to be of type
 *  SELF_TYPE, I replace that type with the current class that my TypeCheckingVisitor is currently checking
 *  (via the attributes). This doesn't work for all cases, but achieves the simple ones
 *
 *
 *  More detail of each class is above its declaration
 *
 *  NOTES:
 *  - InheritanceTree is instantiated within the ClassTable constructor
 *  It then adds all the default classes and then the user defined classes
 *
 *  - Checking if a method is in scope of another class (i.e cow.moo()) is handled by the
 *  typechecker instead of the scope-checker, as it was easier in my implementation to do so
 *
*/

class Semant {

    public static ClassTable classTable;
    public static SymbolTable<RowData<Symbol, Kind, MethodSignature, Symbol>> symtablePass1 = new SymbolTable<>();
    public static SymbolTable<RowData<Symbol,Kind, MethodSignature, Symbol>> symtablePass2 = new SymbolTable<>();
    public static Symbol filename;
    public static InheritanceTree tree;

    public static void analyze(ProgramNode program) {
        // copies program.getclasses array
        ArrayList<ClassNode> cls =(ArrayList<ClassNode>) (new ArrayList<>(program.getClasses()).clone());

        // gets filename
        if (!cls.isEmpty()) filename = cls.get(0).getFilename();

        // creates inheritance tree and populates it
        classTable = new ClassTable(cls);


        if (Utilities.errors()) Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);

        // checking if Main class and main method is located within the inheritance tree
        checkForMain(program.getClasses(), tree, program);

        // scope checking
        ScopeCheckingVisitor scopecheckVisitor = new ScopeCheckingVisitor();
        program.accept(scopecheckVisitor, null);

        if (Utilities.errors()) Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);

        // type checking
        TypeCheckingVisitor typecheckVisitor = new TypeCheckingVisitor();
        program.accept(typecheckVisitor, null);

        if (Utilities.errors()) Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);
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
        tree.seperateFeatures(MainClass.getFeatures(),methods, new ArrayList<>());
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
        tree.getClassVisiblity(tree.root,classNode, path);
        path.pop(); // remove current class

        Collections.reverse(path); // start building from top of tree to bottom
        int res = path.size();
        while(!path.isEmpty()) {
            ClassNode cur = path.pop();

            List<MethodNode> methods = new ArrayList<>();
            List<AttributeNode> attrs = new ArrayList<>();
            tree.seperateFeatures(cur.getFeatures(),methods,attrs);
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
                        new RowData<>(
                                m.getReturn_type(),
                                Kind.METHOD,
                                new MethodSignature(
                                        formalsType,
                                        formalsName
                                ), cur.getName()
                        )
                );
            }
            for (AttributeNode a : attrs) {
                sym.addId(a.getName(),
                        new RowData<>(
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

    /*
     *  checks if a feature is in scope within a class
     *   IMPLEMENATION :
     *  performs DFS traversal on the inheritence tree (calls function to do this), keeping
     *  track of the path with a stack
     *  Once node is found it checks the stack if feature exists
     *  Feature can is checked for type, and compares identifier
     *  if feature exists, returns true else false
     */
    public static boolean searchFeatures(ClassNode classNode, Symbol Identifier) {
        Stack<InheritanceTreeNode> path = new Stack<>();
        tree.getClassVisiblity(tree.root,classNode,path);

        for(InheritanceTreeNode node : path) {
            List<MethodNode> methods = new ArrayList<>();
            List<AttributeNode> attributes = new ArrayList<>();
            tree.seperateFeatures(node.getFeatures(),methods, attributes);
            for (MethodNode m : methods) {
                if (m.getName().equals(Identifier)) {
                    return true;
                }
            }
            for (AttributeNode a: attributes) {
                if (a.getName().equals(Identifier)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static MethodNode getMethod(ClassNode c, Symbol ID) {
        List<FeatureNode> feats = c.getFeatures();
        List<MethodNode> methods = new ArrayList<>();
        tree.seperateFeatures(feats, methods, new ArrayList<>());
        for (MethodNode m : methods) {
            if (m.getName() == ID) {
                return m;
            }
        }
        return null;
    }

    // checks if a feature is within a current class (not including inherited features)
    public static boolean isValidForwardReference(ClassNode c, Symbol Identifier) {
        List<FeatureNode> feats = c.getFeatures();
        List<MethodNode> methods = new ArrayList<>();
        List<AttributeNode> attrs = new ArrayList<>();
        tree.seperateFeatures(feats, methods, attrs);
        // check methods
        for (MethodNode m : methods) {
            if (m.getName() == Identifier) {
                return true;
            }
        }

        // check attributes
        for (AttributeNode a : attrs) {
            if (a.getName() == Identifier) {
                return true;
            }
        }
        return false;
    }

    public static FeatureNode featureExistOnClass(Symbol className, Symbol feature) {
        InheritanceTreeNode classNode = tree.findClass(tree.root, className);
        List<MethodNode> methods = new ArrayList<>();
        List<AttributeNode> attrs = new ArrayList<>();
        tree.seperateFeatures(classNode.getFeatures(),methods,attrs);
        for (MethodNode m : methods) {
            if (m.getName() == feature) {
                return m;
            }
        }
        for (AttributeNode a : attrs) {
            if (a.getName() == feature) {
                return a;
            }
        }
        return null;
    }


    // gets lowest upper bound of two classes (represented as symbols)
    // ------------------- IMPLEMENTATION ------------------- //
    // im going to use the fact that every class inherits object
    // we will obtain the path from root -> class1 and root -> class2
    // we will iterate through the path, the lub(A,B) is the last class
    // both paths share in common
    public static Symbol lub(Symbol type1, Symbol type2) {
        InheritanceTreeNode class1 = tree.findClass(tree.root,type1);
        InheritanceTreeNode class2 = tree.findClass(tree.root,type2);

        Stack<InheritanceTreeNode> path1 = new Stack<>();
        Stack<InheritanceTreeNode> path2 = new Stack<>();
        tree.getClassVisiblity(tree.root, class1, path1);
        tree.getClassVisiblity(tree.root, class2, path2);
        // paths are now filled

        // make it so class object is first (top down)
        Collections.reverse(path1);
        Collections.reverse(path2);
        InheritanceTreeNode lub = null;
        while (!path1.isEmpty() && !path2.isEmpty()) {
            InheritanceTreeNode cur1 = path1.pop();
            InheritanceTreeNode cur2 = path2.pop();
            if (cur1.getName() == cur2.getName()) {
                lub = cur1;
            } else {
                // once one is not the same, lub cannot be further down
                break;
            }
        }
        // null pointer is not possible if method is used correctly
        return lub.getName();

    }


    // allows for calculations of lub(x,y,z...)
    // uses the fact lub(x,y,z) == lub(lub(x,y),z)
    public static Symbol lub(List<Symbol> types) {
        Symbol res = null;
        for(int i = 0; i < types.size()-1; i++) {
            res = lub(types.get(i),types.get(i+1));
        }
        return res;
    }

}
// -------------------------------- Helper Classes -------------------------------- //




// --------------- RowData and Helpers --------------- //


/*
    generic java class, similar to a tuple in python
    bit its size is static
    immutable so its hashable,
    used as the value pair for symboltable
*/
final class RowData<T,V,K,X>{
    final public T ReturnType;
    final public V IDKind;
    final public K MethodData;
    final public X ClassDeclaration;

    RowData(T first, V second, K third, X fourth) {
        this.ReturnType = first;
        this.IDKind = second;
        this.MethodData = third;

        this.ClassDeclaration = fourth;
    }
}


// holds the data for type checking and scope checking a method, held in a column of the rowdata
final class MethodSignature{
    public final List<Symbol> formalTypes;
    public final List<Symbol> formalNames;

    MethodSignature(List<Symbol> formalTypes, List<Symbol> formalNames) {
        this.formalTypes = formalTypes;
        this.formalNames = formalNames;
    }
}

// Enum for holding the type of symtable row
enum Kind {
    VAR,
    METHOD,
    CLASS,

}




/*
 *  ------------------- InheritanceTree Class Overview -------------------
 *
 *  Stores the inheritance graph in the form of a tree datastructure
 *  A node in the tree is simply a class the inherits from the classNode
 *  class (InheritanceTreeNode). It adds to it an array of children (represents edges)
 *  Each classNode will have its features (methods + attributes) accessible.
 *
 *
 *  ------------------- Uses of the class -------------------
 *
 *  I mainly use this class to check for subtype validity and for forward
 *  referencing, i.e if a variable is not in the scope table because it
 *  refers to a class attribute that is defined later in the class, it will
 *  be in here as class table is generated before scope checking and contains
 *  all the classes features.
 */
class InheritanceTree {
    InheritanceTreeNode root;

    InheritanceTree() {
        this.root = null;
    }

    //    boolean returns if value was added or not, with edge case of bad inheritance of default classes
    //    this will return true but semantic error will be incremented
    //    checks for cyclic nature when adding
    public boolean add(ClassNode node){
        // case that this is the first node (most likely object)
        InheritanceTreeNode inheritanceNode = new InheritanceTreeNode(node);
        if (root == null) {
            this.root = inheritanceNode;
            return true;
        }
        // if no parent is specified, then make it Object (root of all classes)
        Symbol parent = node.getParent().getName().equals("_no_class") ? TreeConstants.Object_ : node.getParent();

//        inherits from a bad class (str, int, bool)
        if (parent.equals(TreeConstants.Bool) || parent.equals(TreeConstants.Int) || parent.equals(TreeConstants.Str)) {
            Utilities.semantError(node).println("Class "+ node.getName().getName() + " cannot inherit class " + parent.getName());
            return true;
        }

//        search the tree for parent node
        InheritanceTreeNode parentNode = findClass(root, parent);
        if (parentNode==null) { // cant find parent cannot add
            return false;
        }
        parentNode.addChild(inheritanceNode);
        return true;

    }



    // searches for a node (typically parent), if it exists returns it else returns null
    // uses dfs
    public InheritanceTreeNode findClass(InheritanceTreeNode root, Symbol parent) {
        // base cases
        if (root == null) {
            return null;
        }
        if (root.getName().getName().equals(parent.getName())) {
            return root;
        }

        // step case
        for (InheritanceTreeNode child : root.children) {
            InheritanceTreeNode res = findClass(child, parent);
            if (res != null) {
                return res;
            }
        }
        return null;
    }



    // checks if type1 is a subtype of type2 by finding the parent in the inheritance
    // graph and checking if the child node is in the parents subtree
    public boolean isSubType(Symbol type1, Symbol type2) {

        // defining some edge cases, and obvious cases to save computation

        // save computational work, object is a superset of all
        if (type2 == TreeConstants.Object_)  {
            return true;
        }

        // object SELF_TYPE can safely be replaced in which the variable was defined in i.e current class
        // this is probably a crude way to do it, but ive already wasted so much time. this is good enough
        if (type1 == TreeConstants.SELF_TYPE) {
            type1 = TypeCheckingVisitor.currentClass.getName();
        }
        if (type2 == TreeConstants.SELF_TYPE) {
            type2 = TypeCheckingVisitor.currentClass.getName();
        }


        InheritanceTreeNode parentNode = findClass(root, type2);

//         now run dfs_traverser again but on the parent subtree, and search for type1 node
        return findClass(parentNode, type1) != null;
    }


    // uses DFS to find a node, keeping track of the path
    // of classes it traverses (in a stack)
    // stack is manipulated, stack becomes a stack of
    // class nodes in the inheritance hierarchy
    // i.e finding class visibility for a class A that
    // inherits IO returns:
    // [ Object, IO, A ] (right is top)
    // return true of search node is in inhertiance graph
    public boolean getClassVisiblity(InheritanceTreeNode root, ClassNode search , Stack<InheritanceTreeNode> path) {
        if (root == null) {
            return false;
        }

        if (root == this.root) { // case that this is first value, add root
            path.push(findClass(this.root,TreeConstants.Object_));
        }
        if (root.getName().equals(search.getName())) {
            return true;
        }
        for (InheritanceTreeNode child : root.children) {
            path.push(child);
            boolean res = getClassVisiblity(child, search ,path);
            if (res) {
                return true;
            }
            path.pop();

        }
        return false;
    }

    // seperates attribute and methods into two lists passed in through args
    public void seperateFeatures(List<FeatureNode> features, List<MethodNode> methods, List<AttributeNode> attributes) {
        for (FeatureNode f : features) {
            if (f instanceof AttributeNode a) {
                attributes.add(a);
            } else if (f instanceof MethodNode m) {
                methods.add(m);
            } else {
                System.err.println("Feature is neither attribute nor method");
                System.exit(-1);
            }

        }

    }
}

class InheritanceTreeNode extends ClassNode {
    List<InheritanceTreeNode> children; // directed, this node is dest, edge[n] is the start

    public InheritanceTreeNode(ClassNode val) {
        super(val.getLineNumber(), val.getName(), val.getParent(), val.getFilename());
        this.children = new ArrayList<>();
        // feature list is lost, rebuild it
        for (FeatureNode f: val.getFeatures()) {
            this.add(f);
        }
    }

    public void addChild(InheritanceTreeNode child) {
        this.children.add(child);
    }



}


