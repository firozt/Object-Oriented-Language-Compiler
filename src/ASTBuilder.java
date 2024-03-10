import ast.ClassNode;
import ast.ProgramNode;
import ast.*;
import ast.parser.ASTParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ASTBuilder extends CoolParserBaseVisitor<Tree> {

    @Override
    public Tree visitProgram(CoolParser.ProgramContext ctx) {

        ProgramNode p = new ProgramNode(ctx.getStart().getLine());
        for (CoolParser.CoolClassContext c:ctx.coolClass()) {
            p.add((ClassNode)visitCoolClass(c));
        }
        return p;
    }



    @Override
    public Tree visitCoolClass(CoolParser.CoolClassContext ctx) {
//        Getting all the variables needed to create node
        int line = ctx.getStart().getLine();
        String text = ctx.TYPE().get(0).getText();
        Symbol name = StringTable.idtable.addString(text);

        Symbol parent;
        if (ctx.TYPE().size() == 1) {
            parent = TreeConstants.Object_;

        } else {
            parent = StringTable.idtable.addString(ctx.TYPE().get(1).getText());
        }
        String src = ctx.getStart().getTokenSource().getSourceName();
        Symbol file = StringTable.stringtable.addString(src);

//        Visit all features (methods and properties) of the given class
        ClassNode cn = new ClassNode(line, name, parent , file);
        for(CoolParser.FeatureContext feature : ctx.feature()) {
            cn.add((FeatureNode)visitFeature(feature));
        }

        return cn;
    }
    @Override
    public Tree visitFeature(CoolParser.FeatureContext ctx) {
        int line = ctx.getStart().getLine();
        Symbol name = StringTable.idtable.addString(ctx.ID().getText());
        Symbol type = StringTable.idtable.addString(ctx.TYPE().getText());

        if (ctx.PARENT_OPEN() == null) {
//            node is an attribute
            ExpressionNode expr;
            if (ctx.ASSIGN_OPERATOR() != null) {
                expr = (ExpressionNode) visitExpr(ctx.expr());
            } else {
//                expr = new ExpressionNode(line) {};
                expr = new NoExpressionNode(line);
            }
            return new AttributeNode(line, name, type, expr);
        } else {
//            node is a method
            ExpressionNode expr = (ExpressionNode) visitExpr(ctx.expr());
            List<FormalNode> params = ctx
                    .formal()
                    .stream()
                    .map(x -> (FormalNode)visitFormal(x))
                    .toList();
            return new MethodNode(line, name, params,type, expr);
        }
    }

    @Override
    public Tree visitFormal(CoolParser.FormalContext ctx) {
        //        method parameter
        Symbol name = StringTable.idtable.addString(ctx.ID().getText());
        Symbol type = StringTable.idtable.addString(ctx.TYPE().getText());
        int line = ctx.getStart().getLine();
        return new FormalNode(line, name, type);
    }

    @Override
    public Tree visitExpr(CoolParser.ExprContext ctx) {
        int line = ctx.getStart().getLine();

//        Non recursive expr
        if (ctx.expr().isEmpty()) {
            if (ctx.ID().size() == 1 && ctx.PARENT_OPEN() == null) {
//                expr -> ID
                Symbol name = StringTable.idtable.addString(ctx.ID(0).getText());
                return new ObjectNode(line, name);
            } else if (ctx.STR_CONST() != null) {
//                expr -> STR_CONST
                Symbol text = StringTable.stringtable.addString(ctx.STR_CONST().getText());
                return new StringConstNode(line, text);
            } else if (ctx.INT_CONST() != null) {
//                expr -> INT_CONST
                Symbol value = StringTable.inttable.addString(ctx.INT_CONST().getText());
                return new IntConstNode(line, value);
            } else if (ctx.BOOL_TRUE() != null) {
//                expr -> BOOL_TRUE
                return new BoolConstNode(line, java.lang.Boolean.TRUE);
            } else if (ctx.BOOL_FALSE() != null) {
//                expr -> BOOL_FALSE
                return new BoolConstNode(line, java.lang.Boolean.FALSE);
            } else if (ctx.NEW() != null && ctx.TYPE().size() == 1) {
//                expr -> NEW TYPE
                Symbol type = StringTable.idtable.addString(ctx.TYPE(0).getText());
                return new NewNode(line, type);
            }
        }
//            Recursive rules:
        if (ctx.PLUS_OPERATOR() != null) { // expr -> e1 + e2
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new PlusNode(line, exprs[0], exprs[1]);
        } else if(ctx.ASSIGN_OPERATOR().size()==1 && ctx.ID().size()==1 && ctx.expr().size()==1 && ctx.LET()==null) { // may be confused with let expr
            ExpressionNode e = getNextExpr(1,ctx)[0];
            Symbol name = StringTable.idtable.addString(ctx.ID(0).getText());
            return new AssignNode(line, name, e);
        } else if (ctx.MINUS_OPERATOR()!= null) { // expr -> e1 - e2
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new SubNode(line, exprs[0], exprs[1]);
        } else if (ctx.DIV_OPERATOR()!= null) { // expr -> e1 / e2
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new DivideNode(line, exprs[0], exprs[1]);
        } else if (ctx.MULT_OPERATOR()!= null) { // expr -> e1 * e2
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new MulNode(line, exprs[0], exprs[1]);
        } else if(ctx.ISVOID() != null) { // expr -> isvoid expr
            ExpressionNode e = (ExpressionNode) visitExpr(ctx.expr(0));
            return new IsVoidNode(line, e);
        } else if(ctx.INT_COMPLEMENT_OPERATOR() != null) { // expr -> ~expr
            ExpressionNode e = (ExpressionNode) visitExpr(ctx.expr(0));
            return new NegNode(line, e);
        } else if(ctx.LESS_EQ_OPERATOR() != null) { // expr -> e1 <= e2
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new LEqNode(line, exprs[0],exprs[1]);
        } else if(ctx.LESS_OPERATOR() != null) { // expr -> e1 < e2
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new LTNode(line, exprs[0], exprs[1]);
        } else if(ctx.NOT() != null) { // expr -> not e1
            ExpressionNode e = (ExpressionNode) visitExpr(ctx.expr(0));
            return new CompNode(line, e);
        } else if(ctx.EQ_OPERATOR() != null) { // expr -> e1 = e2
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new EqNode(line, exprs[0], exprs[1]);
        } else if(ctx.PARENT_OPEN() != null && ctx.PARENT_CLOSE() != null && ctx.expr().size()==1 && ctx.ID().isEmpty()) { // expr -> (expr)
//            TODO: MAY BE WRONG?
            return (ExpressionNode) visitExpr(ctx.expr(0));
        } else if(ctx.CURLY_OPEN() != null && ctx.CURLY_CLOSE() != null && !ctx.expr().isEmpty()){  // expr -> { (expr;)+ }
            return new BlockNode(line, List.of(getNextExpr(ctx.expr().size(),ctx)));
        } else if(ctx.LET() != null) { // expr -> LET ID COLON TYPE (ASSIGN_OPERATOR expr)? (COMMA ID COLON TYPE (ASSIGN_OPERATOR expr)?)* IN expr

            ExpressionNode[] exprsPrimitive = getNextExpr(ctx.expr().size(),ctx);
            List<ExpressionNode> exprs = new ArrayList<>(Arrays.asList(exprsPrimitive));
            ExpressionNode body = exprs.get(exprs.size()-1);
            return LetAux(exprs,ctx,0);

        } else if(ctx.CASE() != null) { // expr -> CASE expr OF (ID COLON TYPE RIGHTARROW expr)+ ESAC
//            TODO: FINISH
            return null;
        } else if(ctx.WHILE() != null) { // expr -> while expr loop expr pool
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new LoopNode(line,exprs[0], exprs[1]);
        } else if(ctx.IF() != null) { // if expr then expr else expr fi
            ExpressionNode[] exprs = getNextExpr(3,ctx);
            return new CondNode(line,exprs[0],exprs[1],exprs[2]);
        } else if(ctx.ID().size() == 1 && ctx.ASSIGN_OPERATOR().size()==1 && ctx.expr().size()==1) {
            ExpressionNode expr =  getNextExpr(1,ctx)[0];
            Symbol name = StringTable.idtable.addString(ctx.ID(0).getText());
            return new AssignNode(line,name,expr);
        } else if(ctx.WHILE() != null) {
            ExpressionNode[] exprs = getNextExpr(2,ctx);
            return new LoopNode(line,exprs[0],exprs[1]);
        } else if(ctx.PARENT_OPEN()!=null && !ctx.ID().isEmpty()) { // dispatch
            List<ExpressionNode> exprs =  new ArrayList<>(Arrays.asList(getNextExpr(ctx.expr().size(),ctx)));

            if(ctx.AT()!=null) {
//                static dispatch
                List<ExpressionNode> actuals = exprs.subList(1,exprs.size());
                Symbol type = StringTable.idtable.addString(ctx.TYPE(0).getText());
                Symbol name = StringTable.idtable.addString(ctx.ID(0).getText());

                return new StaticDispatchNode(line,exprs.get(0),type,name,actuals);
            } else {
                ExpressionNode init;
                Symbol name;
//                regular dispatch
                if (ctx.PERIOD()!=null) {
                    init = exprs.get(0);
                    exprs.remove(0);
//                    name = StringTable.idtable.addString(ctx.ID(0).getText());
                    name = StringTable.idtable.addString(ctx.ID(0).getText());

                } else {
                    init = new ObjectNode(line, TreeConstants.self);
                    name = StringTable.idtable.addString(ctx.ID(0).getText());
                }

                return new DispatchNode(line,init,name,exprs);
            }

        }

        System.out.println("ERROR: REACHED THE END FIX IT PAL");
        System.out.println(ctx.getStart().getLine());
        System.out.println(ctx.getStart().getText());
        return null;
    }
    // helper that gets next n expressions and returns it in an array
    private ExpressionNode[] getNextExpr(int n, CoolParser.ExprContext ctx) {
        ExpressionNode[] exprs = new ExpressionNode[n];
        for(int i = 0; i < n; i++) {
            exprs[i] = (ExpressionNode) visitExpr(ctx.expr(i));
        }
        return exprs;
    }

    private ExpressionNode LetAux(List<ExpressionNode> exprs, CoolParser.ExprContext ctx, int index) {
        if(exprs.isEmpty()) {
            return null;
        }
        int line = exprs.get(0).getLineNumber();
        Symbol ID = StringTable.idtable.addString(ctx.ID(index).getText());
        Symbol type = StringTable.idtable.addString(ctx.TYPE(index).getText());
        ExpressionNode init = exprs.get(0);

        System.out.println(
        );
        if (ctx.ID(index).getText().equals("z")) {
            System.out.println("HELLO");
        }

//        if (ctx.expr(index). {
//            System.out.println("IN");
//            init = new NoExpressionNode(line);
////                    TODO: LINE NUMBER MAY BE WRONG
//        }

        exprs.remove(0);
        index++;
        return new LetNode(line, ID, type, init, LetAux(exprs, ctx, index));
    }


}

// 51 + 44 passed (out of 134)
// 51 + 51 (down 11 test cases)
// 57 + 51 (out of 134
