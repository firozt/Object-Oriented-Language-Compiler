import ast.*;
import ast.visitor.BaseVisitor;

public class TypeCheckingVisitor extends BaseVisitor<Object, Object> {
    public TypeCheckingVisitor()  {

    }

    @Override
    public Object visit(AttributeNode node, Object data) {
        // attribute has an optional init that we need to check
        // atribute
        if (node.getInit() instanceof NoExpressionNode) {
            node.getInit().setType(node.getType_decl());
            return super.visit(node, data);
        }

        // find type of expr and compare it to declaration
        visit(node.getInit(),data);
        Symbol exprType = node.getInit().getType();
        if (!exprType.equals(node.getType_decl())) {
            Utilities.semantError(Semant.filename,node)
                    .println(
                            "Inferred type "+node.getType_decl()+" of initialization of attribute "+node.getInit().getType()+" does not conform to declared type "+node.getType_decl()+"."
                    );
        }
        return super.visit(node, data);
    }

    @Override
    public Object visit(StringConstNode node, Object data) {
        // string constant
        node.setType(TreeConstants.Str);
        return super.visit(node, data);
    }

    @Override
    public Object visit(BoolConstNode node, Object data) {
        // bool constant
        node.setType(TreeConstants.Bool);
        return super.visit(node, data);
    }
}
