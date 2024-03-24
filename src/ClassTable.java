import ast.*;

import java.util.*;


/**
 * This class may be used to contain the semantic information such as
 * the inheritance graph.  You may use it or not as you like: it is only
 * here to provide a container for the supplied methods.
 */
class ClassTable {
    /**
     * Creates data structures representing basic Cool classes (Object,
     * IO, Int, Bool, String).  Please note: as is this method does not
     * do anything useful; you will need to edit it to make if do what
     * you want.
     */
    private void installBasicClasses() {
        Symbol filename = Semant.filename;
//        Symbol filename
//                = StringTable.stringtable.addString("<basic class>");

        LinkedList<FormalNode> formals;

        // The following demonstrates how to create dummy parse trees to
        // refer to basic Cool classes.  There's no need for method
        // bodies -- these are already built into the runtime system.

        // IMPORTANT: The results of the following expressions are
        // stored in local variables.  You will want to do something
        // with those variables at the end of this method to make this
        // code meaningful.

        // The Object class has no parent class. Its methods are
        //        cool_abort() : Object    aborts the program
        //        type_name() : Str        returns a string representation
        //                                 of class name
        //        copy() : SELF_TYPE       returns a copy of the object

        ClassNode Object_class =
                new ClassNode(0,
                        TreeConstants.Object_,
                        TreeConstants.No_class,
                        filename);

        Object_class.add(new MethodNode(0,
                TreeConstants.cool_abort,
                new LinkedList<FormalNode>(),
                TreeConstants.Object_,
                new NoExpressionNode(0)));

        Object_class.add(new MethodNode(0,
                TreeConstants.type_name,
                new LinkedList<FormalNode>(),
                TreeConstants.Str,
                new NoExpressionNode(0)));

        Object_class.add(new MethodNode(0,
                TreeConstants.copy,
                new LinkedList<FormalNode>(),
                TreeConstants.SELF_TYPE,
                new NoExpressionNode(0)));


        // The IO class inherits from Object. Its methods are
        //        out_string(Str) : SELF_TYPE  writes a string to the output
        //        out_int(Int) : SELF_TYPE      "    an int    "  "     "
        //        in_string() : Str            reads a string from the input
        //        in_int() : Int                "   an int     "  "     "

        ClassNode IO_class =
                new ClassNode(0,
                        TreeConstants.IO,
                        TreeConstants.Object_,
                        filename);

        formals = new LinkedList<FormalNode>();
        formals.add(
                new FormalNode(0,
                        TreeConstants.arg,
                        TreeConstants.Str));

        IO_class.add(new MethodNode(0,
                TreeConstants.out_string,
                formals,
                TreeConstants.SELF_TYPE,
                new NoExpressionNode(0)));


        formals = new LinkedList<FormalNode>();
        formals.add(
                new FormalNode(0,
                        TreeConstants.arg,
                        TreeConstants.Int));
        IO_class.add(new MethodNode(0,
                TreeConstants.out_int,
                formals,
                TreeConstants.SELF_TYPE,
                new NoExpressionNode(0)));

        IO_class.add(new MethodNode(0,
                TreeConstants.in_string,
                new LinkedList<FormalNode>(),
                TreeConstants.Str,
                new NoExpressionNode(0)));

        IO_class.add(new MethodNode(0,
                TreeConstants.in_int,
                new LinkedList<FormalNode>(),
                TreeConstants.Int,
                new NoExpressionNode(0)));

        // The Int class has no methods and only a single attribute, the
        // "val" for the integer.

        ClassNode Int_class =
                new ClassNode(0,
                        TreeConstants.Int,
                        TreeConstants.Object_,
                        filename);

        Int_class.add(new AttributeNode(0,
                TreeConstants.val,
                TreeConstants.prim_slot,
                new NoExpressionNode(0)));

        // Bool also has only the "val" slot.
        ClassNode Bool_class =
                new ClassNode(0,
                        TreeConstants.Bool,
                        TreeConstants.Object_,
                        filename);

        Bool_class.add(new AttributeNode(0,
                TreeConstants.val,
                TreeConstants.prim_slot,
                new NoExpressionNode(0)));

        // The class Str has a number of slots and operations:
        //       val                              the length of the string
        //       str_field                        the string itself
        //       length() : Int                   returns length of the string
        //       concat(arg: Str) : Str           performs string concatenation
        //       substr(arg: Int, arg2: Int): Str substring selection

        ClassNode Str_class =
                new ClassNode(0,
                        TreeConstants.Str,
                        TreeConstants.Object_,
                        filename);
        Str_class.add(new AttributeNode(0,
                TreeConstants.val,
                TreeConstants.Int,
                new NoExpressionNode(0)));

        Str_class.add(new AttributeNode(0,
                TreeConstants.str_field,
                TreeConstants.prim_slot,
                new NoExpressionNode(0)));
        Str_class.add(new MethodNode(0,
                TreeConstants.length,
                new LinkedList<FormalNode>(),
                TreeConstants.Int,
                new NoExpressionNode(0)));

        formals = new LinkedList<FormalNode>();
        formals.add(new FormalNode(0,
                TreeConstants.arg,
                TreeConstants.Str));
        Str_class.add(new MethodNode(0,
                TreeConstants.concat,
                formals,
                TreeConstants.Str,
                new NoExpressionNode(0)));

        formals = new LinkedList<FormalNode>();
        formals.add(new FormalNode(0,
                TreeConstants.arg,
                TreeConstants.Int));
        formals.add(new FormalNode(0,
                TreeConstants.arg2,
                TreeConstants.Int));

        Str_class.add(new MethodNode(0,
                TreeConstants.substr,
                formals,
                TreeConstants.Str,
                new NoExpressionNode(0)));

	/* Do somethind with Object_class, IO_class, Int_class,
           Bool_class, and Str_class here */

//        Adding all the nodes to the tree
        tree.add(Object_class);
        tree.add(Str_class);
        tree.add(IO_class);
        tree.add(Bool_class);
        tree.add(Int_class);

        Semant.filename = Object_class.getFilename();



    }

    InheritanceTree tree;
    public ClassTable(List<ClassNode> cls) {

        this.tree = new InheritanceTree();
        installBasicClasses();

        int numClasses = cls.size();
        int numRetries = 0;

        while (!cls.isEmpty()) {
            ClassNode c = cls.get(0);
            if (!tree.add(c)) {
                // parent class does not exist in graph yet, move to the back of the list
                // and keep going
                cls.remove(0);
                cls.add(c);
                numRetries++;
            } else { // added

                cls.remove(0);
            }
            if (numRetries > 3*numClasses) { // maximum potential tries before it is impossible to add (cyclic nature)
                // generate message, iterating through all cyclic nodes (left-over nodes0
                for (ClassNode n : cls) {
                    String errorMsg = "Class " + n.getName().getName() + ", or an ancestor of " + n.getName().getName() + ", is involved in an inheritance cycle.";
                    Utilities.semantError(c).println(errorMsg);
                }
                break;
            }
        }
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
    public boolean searchFeatures(ClassNode classNode, Symbol Identifier) {
        Stack<InheritanceTreeNode> path = new Stack<>();
        tree.getClassVisiblity(tree.root,classNode,path);
        // stack has the path from object -> class, visibility of all features
        while (!path.isEmpty()) {
            InheritanceTreeNode cur = path.pop();
            for(FeatureNode f : cur.getFeatures()) {
                if (f instanceof MethodNode m) {
                    if (m.getName().equals(Identifier)) {
                        return true;
                    }
                } else if(f instanceof AttributeNode a) {
                    if (a.getName().equals(Identifier)) {
                        return true;
                    }


                } else {
                    System.err.println("Feature is neither method nor Attribute");
                    System.exit(-1);
                }
            }
        }

        return false;
    }


}



/*
 *  ------------------- Class Overview -------------------
 *
 *  Stores the inheritance graph in the form of a tree datastructure
 *  A node in the tree is simply a class the inherits from the classNode
 *  class. It adds to it an array of children (represents edges)
 *  Each classNode will have its features (methods + attributes)
 *  accessibile.
 *
 *  ------------------- Uses of the class -------------------
 *
 * I mainly use this class to check for subtype validity and for forward
 * referencing, i.e if a variable is not in the scope table because it
 * refers to a class attribute that is defined later in the class, it will
 * be in here as class table is generated before scope checking and contains
 * all the classes features.
 */
class InheritanceTree {
    InheritanceTreeNode root;

    InheritanceTree() {
        this.root = null;
    }

//    boolean returns if value was added or not, with edge case of bad inheritance of default classes
//    this will return true but semantic error will be incremented
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
//            TODO: May be invalid?
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
    private InheritanceTreeNode findClass(InheritanceTreeNode root, Symbol parent) {
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
        // save computational work
        if (type1 == TreeConstants.Object_)  {
            return true;
        }

        if (type1 == TreeConstants.SELF_TYPE) {
            type1 = TypeCheckingVisitor.currentClass.getName();
        }
        InheritanceTreeNode parentNode = findClass(root, type2);
        // now run dfs_traverser again but on the parent subtree, and search for type1 node
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
