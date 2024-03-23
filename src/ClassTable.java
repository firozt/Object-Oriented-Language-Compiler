import ast.*;
import org.antlr.v4.runtime.misc.OrderedHashSet;

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
            if (Utilities.errors()) break;
            ClassNode c = cls.get(0);
            if (!tree.add(c)) {
                // move item to the back of the list and keep going
                cls.remove(0);
                cls.add(c);
                numRetries++;
            } else {
                cls.remove(0);
            }
            if (numRetries > 3*numClasses) { // maximum potential tries before its for sure invalid
                for (ClassNode n : cls) {
                    String errorMsg = "Class " + n.getName().getName() + ", or an ancestor of " + n.getName().getName() + ", is involved in an inheritance cycle.";
                    Utilities.semantError(c).println(errorMsg);
                }
            }
        }
    }
}

    
class InheritanceTree {
    InheritanceTreeNode root;
    boolean hasCycle; // true if the tree has a cycle

    InheritanceTree() {
        this.root = null;
    }

//    boolean returns if value was added or not, with edge case of bad inhertiance of default classes
//    this will return true but semant error will be incremented
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
        InheritanceTreeNode parentNode = dfs_traverser(root, parent);
        if (parentNode==null) { // cant find parent cannot add
            return false;
        }
        parentNode.addChild(inheritanceNode);
        return true;

    }

    public InheritanceTreeNode dfs_traverser(InheritanceTreeNode root, Symbol parent) {
        // base cases
        if (root == null) {
            return null;
        }
        if (root.getName().getName().equals(parent.getName())) {
            return root;
        }

        // step case
        for (InheritanceTreeNode child : root.children) {
            InheritanceTreeNode res = dfs_traverser(child, parent);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

}

class InheritanceTreeNode extends ClassNode {
    List<InheritanceTreeNode> children; // directed, this node is dest, edge[n] is the start

    public InheritanceTreeNode(ClassNode val) {
        super(val.getLineNumber(), val.getName(), val.getParent(), val.getFilename());
        this.children = new ArrayList<>();
    }

    public void addChild(InheritanceTreeNode child) {
        this.children.add(child);
    }


}
