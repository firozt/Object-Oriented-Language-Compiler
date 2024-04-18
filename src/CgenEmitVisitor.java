import ast.*;

import java.util.HashSet;
import java.util.List;

public class CgenEmitVisitor extends CgenVisitor<String, String>{

    /* Emit code for expressions */
    CgenEnv env;

    //  target: if there is any choice, put the result here, but
    //  there are no guarantees.
    //  Use forceDest instead if you really care.
    //  Return value: the name of the register holding the result.
    //  Possibly the same as target.

    @Override
    public String visit(AssignNode node, String target) {
        Cgen.VarInfo lhs = env.vars.lookup(node.getName());
        String rhs_value = node.getExpr().accept(this, target);
        lhs.emitUpdate(rhs_value);
        return rhs_value;
    }

    //// Dynamic dispatch:
    //    1. The arguments are evaluated and pushed on the stack.
    //    2. The dispatch expression is evaluated, and the result put in $a0.
    //    3. The dipatch expression is tested for void.
    //    4.     If void, computation is aborted.
    //    5. The dispatch table of the dispatch value is loaded.
    //    6. The dispatch table is indexed with the method offset.
    //    7. Jump to the method.
    //// Static dispatch has the same steps as normal dispatch, except
    //// the dispatch table is taken from the user-specified class.

    @Override
    public String visit(DispatchNode node, String target) {
        System.out.println("IN " + node.getName() + ": " + node.getExpr().getType());
        Symbol classname = node.getExpr().getType();
        if (classname == TreeConstants.SELF_TYPE)
            classname = env.getClassname();
        CgenNode c = Cgen.classTable.get(classname);
        Cgen.MethodInfo minfo = c.env.methods.lookup(node.getName());
        for (ExpressionNode e : node.getActuals()) {
            String r_actual = e.accept(this, CgenConstants.ACC);
            Cgen.emitter.emitPush(r_actual);
        }
        forceDest(node.getExpr(), CgenConstants.ACC);
        if (Flags.cgen_debug) System.err.println("    Dispatch to " + node.getName());
        int lab = CgenEnv.getFreshLabel();
        Cgen.emitter.emitBne(CgenConstants.ACC,CgenConstants.ZERO,lab);      // test for void
        Cgen.emitter.emitLoadString(CgenConstants.ACC, env.getFilename());
        Cgen.emitter.emitLoadImm(CgenConstants.T1, node.getLineNumber());
        Cgen.emitter.emitDispatchAbort();
        Cgen.emitter.emitLabelDef(lab);
        Cgen.emitter.emitLoad(CgenConstants.T1, CgenConstants.DISPTABLE_OFFSET, CgenConstants.ACC);
        Cgen.emitter.emitLoad(CgenConstants.T1, minfo.getOffset(), CgenConstants.T1);
        Cgen.emitter.emitJalr(CgenConstants.T1);
        return CgenConstants.ACC;
    }

    @Override
    public String visit(StaticDispatchNode node, String target) {
        // 1) add all the args to the stack
        for (ExpressionNode arg : node.getActuals()) {
            arg.accept(this, target); // evaluate arg, loaded into a0
            Cgen.emitter.emitPush(CgenConstants.ACC); // push onto stack
        }

        // 2) evaluate object calling method
        node.getExpr().accept(this, target); // loads into a0

        // 3) check for void
        int lab = CgenEnv.getFreshLabel();
        CgenNode c = Cgen.classTable.get(node.getExpr().getType()); // class of calling expr
        Cgen.MethodInfo minfo = c.env.methods.lookup(node.getName());


        Cgen.emitter.emitBne(CgenConstants.ACC,CgenConstants.ZERO,lab);      // test for void
        Cgen.emitter.emitLoadString(CgenConstants.ACC, env.getFilename());
        Cgen.emitter.emitLoadImm(CgenConstants.T1, node.getLineNumber());
        Cgen.emitter.emitDispatchAbort();
        Cgen.emitter.emitLabelDef(lab);

        // 4) load correct disp table to a0, either current disp table or a parent type

        String classname = node.getType_name().getName();
        // check for self_type



        if (!classname.equals(node.getExpr().getType().getName()) && !classname.equals(TreeConstants.SELF_TYPE.getName())) {
            // casting type to parent, change the dispatch table addr
            Cgen.emitter.emitLoadAddress(CgenConstants.T1,classname+CgenConstants.DISPTAB_SUFFIX);
        } else {
            // same type , disp table already loaded in a0
            Cgen.emitter.emitLoad(CgenConstants.T1, CgenConstants.DISPTABLE_OFFSET, CgenConstants.ACC);
        }
        Cgen.emitter.emitLoad(CgenConstants.T1, minfo.getOffset(), CgenConstants.T1);
        Cgen.emitter.emitJalr(CgenConstants.T1);

        return CgenConstants.ACC;
    }

    // The cases are tested in the order
    // of most specific to least specific.  Since tags are assigned
    // in depth-first order with the root being assigned 0, tests for higher-numbered
    // classes should be emitted before lower-numbered classes.

    @Override
    public String visit(CaseNode node, String target) {
        int out_label = CgenEnv.getFreshLabel();

        String r_expr = node.getExpr().accept(this, CgenConstants.ACC);
        int lab = CgenEnv.getFreshLabel();
        Cgen.emitter.emitBne(r_expr,CgenConstants.ZERO,lab);      // test for void
        Cgen.emitter.emitLoadString(CgenConstants.ACC, env.getFilename());
        Cgen.emitter.emitLoadImm(CgenConstants.T1, node.getLineNumber());
        Cgen.emitter.emitCaseAbort2();
        Cgen.emitter.emitLabelDef(lab);
        Cgen.emitter.emitLoad(CgenConstants.T2, CgenConstants.TAG_OFFSET, r_expr);  // fetch the class tag

        for (int class_num = CgenEnv.getLastTag()-1; class_num >=0; class_num--)
            for(BranchNode b : node.getCases()) {
                int tag = Cgen.classTable.get(b.getType_decl()).env.getClassTag();
                if (class_num == tag) {
                    if (Flags.cgen_debug) System.err.println("    Coding case " + b.getType_decl());
                    // result is in ACC
                    // r_newvar is the value that we did the case on.  It will be bound to the new var.
                    String r_newvar = CgenConstants.ACC;

                    lab = CgenEnv.getFreshLabel();
                    CgenEnv downcast = Cgen.classTable.get(b.getType_decl()).env;
                    int class_tag = downcast.getClassTag();
                    int last_tag  = downcast.getMaxChildTag();

                    Cgen.emitter.emitBlti(CgenConstants.T2, class_tag, lab);
                    Cgen.emitter.emitBgti(CgenConstants.T2, last_tag, lab);
                    env.addLocal(b.getName());
                    env.vars.lookup(b.getName()).emitUpdate(r_newvar);
                    forceDest(b.getExpr(), CgenConstants.ACC);
                    env.removeLocal();
                    Cgen.emitter.emitBranch(out_label);
                    Cgen.emitter.emitLabelDef(lab);
                }
            }
        Cgen.emitter.emitCaseAbort();
        Cgen.emitter.emitLabelDef(out_label);
        return CgenConstants.ACC;
    }

    @Override
    public String visit(LetNode node, String target) {
        // r_newvar is the register to which we think the new variable will be
        //  assigned.
        // r_newvar is null if register allocation is disabled or no regs availible.
        //r_init is the register that holds the result of the init expr.  We'd like
        //  r_init to be the same as r_newvar.
        String r_newvar = CgenConstants.getRegister(env.getNextTempOffset());

        String r_init = r_newvar;
        if (r_init == null){
            r_init = CgenConstants.ACC;
        }

        if (node.getInit() instanceof NoExpressionNode)
        {
            if (TreeConstants.Int == node.getType_decl())
            {
                Cgen.emitter.emitPartialLoadAddress(r_init);
                Cgen.emitter.codeRefInt(StringTable.inttable.get("0"));
                Cgen.emitter.emitNewline();
            }
            else if (TreeConstants.Str == node.getType_decl())
            {
                Cgen.emitter.emitPartialLoadAddress(r_init);
                Cgen.emitter.codeRefString(StringTable.stringtable.get(""));
                Cgen.emitter.emitNewline();
            }
            else if (TreeConstants.Bool == node.getType_decl())
            {
                Cgen.emitter.emitPartialLoadAddress(r_init);
                Cgen.emitter.codeRef(false);
                Cgen.emitter.emitNewline();
            }
            else
            {
                r_init = CgenConstants.ZERO;
            }
        }
        else
        {
            r_init = node.getInit().accept(this, r_init);
        }

        //Register r_init now holds the location of the value to which newvar should
        //be initialized.  Hopefully, r_init and newvar are one and the same, in
        //which case the code_update is a nop.
        env.addLocal(node.getIdentifier());
        Cgen.VarInfo newvar = env.vars.lookup(node.getIdentifier());
        newvar.emitUpdate(r_init);

        //test that r_newvar really contains the register to which newvar
        //was assigned.
        assert( CgenConstants.regEq(newvar.getRegister(), r_newvar) );

        String r_body = node.getBody().accept(this, target);
        env.removeLocal();
        return r_body;
    }

    @Override
    public String visit(NewNode node, String target) {
        // load addr to classes protObj to a0
        // call object.copy
        // call class_init
        // edge case for new SELF_TYPE, in this case we

        Symbol type_name = node.getType_name();
        if (node.getType_name() == TreeConstants.SELF_TYPE) {
            type_name = env.getClassname();
        }

        String ref = type_name + CgenConstants.PROTOBJ_SUFFIX;
        Cgen.emitter.emitLoadAddress(CgenConstants.ACC, ref);
        Cgen.emitter.emitCopy();
        Cgen.emitter.emitInit(type_name);

        return CgenConstants.ACC;
    }

    @Override
    public String visit(CondNode node, String target) {
        String cond = node.getCond().accept(this, target); // visit condition load it into a0
        // a0 will be true or false object
        // load value of bool which is offset 3
        Cgen.emitter.emitLoad(CgenConstants.T1,3,CgenConstants.ACC);

        int elseLabel = CgenEnv.getFreshLabel();
        int endIfLabel = CgenEnv.getFreshLabel();



        Cgen.emitter.emitBeqz(CgenConstants.T1,elseLabel);
//        do if here
        node.getThenExpr().accept(this,cond);
        Cgen.emitter.emitBranch(endIfLabel);
        Cgen.emitter.emitLabelDef(elseLabel);
//        do else here
        node.getElseExpr().accept(this,cond);
        Cgen.emitter.emitLabelDef(endIfLabel);

        return CgenConstants.ACC;
    }

    @Override
    // TODO: MAYBE NOT CORRECT?
    public String visit(LoopNode node, String target) {
        int loopStart = CgenEnv.getFreshLabel();
        int loopEnd = CgenEnv.getFreshLabel();

        Cgen.emitter.emitLabelDef(loopStart);
        // do loop check
        node.getCond().accept(this,target);
        // load value of a0 into t1
        Cgen.emitter.emitLoad(CgenConstants.T1,3,CgenConstants.ACC);
        Cgen.emitter.emitBeqz(CgenConstants.T1,loopEnd);

        // do loop
        node.getBody().accept(this, target);

        Cgen.emitter.emitBranch(loopStart);
        Cgen.emitter.emitLabelDef(loopEnd);

        return CgenConstants.ACC;
    }

    @Override
    public String visit(BlockNode node, String target) {
        for (ExpressionNode n : node.getExprs().subList(0,node.getExprs().size()-1))
            visit(n,target);
        // block returns last value of the block
        return visit(node.getExprs().get(node.getExprs().size()-1),target);
    }

    // puts e1 top of stack, and puts e2 into acc.
    // uses t1, t0 to compute addition
    // pops e1 off the stack
    // res of operation value is in $t1 (value not addr)
    public String evalE1AndE2(BinopNode node, String data) {
        String e1 = node.getE1().accept(this, data); // loads addr to $a0
        Cgen.emitter.emitPush(CgenConstants.ACC); // push addr of e1 to stack
        String e2 = node.getE2().accept(this, data); // loads addr to $a0

        Cgen.emitter.emitCopy(); // copies object to place new val into

        Cgen.emitter.emitLoad(CgenConstants.T1,3,CgenConstants.ACC); // load value of $a0 to $t1
        // pop stack
        Cgen.emitter.emitLoad(CgenConstants.T2,1,CgenConstants.SP); // loads addr from top of stack to $t2
        Cgen.emitter.emitAddiu(CgenConstants.SP,CgenConstants.SP,4); // pops stack
        Cgen.emitter.emitLoad(CgenConstants.T2, 3, CgenConstants.T2); // gets value of top of stack
        return e2;
    }
    @Override
    public String visit(PlusNode node, String data) {
        String res = evalE1AndE2(node, data); // t1 and t2 is now populated
        Cgen.emitter.emitAdd(CgenConstants.T1, CgenConstants.T1, CgenConstants.T2); // t1 now holds e1 + e2 value
        Cgen.emitter.emitStore(CgenConstants.T1,3,CgenConstants.ACC); // update accumulator int constant value
        return res;
    }

    @Override
    public String visit(SubNode node, String data) {
        String res = evalE1AndE2(node, data);
        Cgen.emitter.emitSub(CgenConstants.T1, CgenConstants.T2, CgenConstants.T1); // t1 now holds e1 - e2 value
        Cgen.emitter.emitStore(CgenConstants.T1,3,CgenConstants.ACC); // update accumulator int constant value
        return res;
    }

    @Override
    public String visit(MulNode node, String data) {
        String res = evalE1AndE2(node, data);
        Cgen.emitter.emitMul(CgenConstants.T1, CgenConstants.T2, CgenConstants.T1); // t1 now holds e1 * e2 value
        Cgen.emitter.emitStore(CgenConstants.T1,3,CgenConstants.ACC); // update accumulator int constant value
        return res;
    }

    @Override
    public String visit(DivideNode node, String data) {
        String res = evalE1AndE2(node, data);
        Cgen.emitter.emitDiv(CgenConstants.T1, CgenConstants.T2, CgenConstants.T1); // t1 now holds e1 / e2 value
        Cgen.emitter.emitStore(CgenConstants.T1,3,CgenConstants.ACC); // update accumulator int constant value
        return res;
    }




    public String comparisonHelper(BoolBinopNode node, String data, boolean compareValue) {

        // 1 ) load e1, e2 into t1 and t2
        String e1 = node.getE1().accept(this, data);
        Cgen.emitter.emitMove(CgenConstants.T1, CgenConstants.ACC);
        Cgen.emitter.emitMove(CgenConstants.T3,CgenConstants.T1); // case of dispatch, t1 may be overriden, save copy to t3 which is safe

        String e2 = node.getE2().accept(this, data);
        Cgen.emitter.emitMove(CgenConstants.T2, CgenConstants.ACC);
        Cgen.emitter.emitMove(CgenConstants.T1,CgenConstants.T3);

//        type must be the same given one expr is of type string, int , bool
        if (compareValue) {
            Cgen.emitter.emitLoad(CgenConstants.T2,3,CgenConstants.T2); // load back t3 incase of changes to t1
            Cgen.emitter.emitLoad(CgenConstants.T1,3,CgenConstants.T1);
        }

        return CgenConstants.ACC;
    }
    //The calling convention for equality_test:
    //  INPUT: The two objects are passed in $t1 and $t2
    //  OUTPUT: Initial value of $a0, if the objects are equal
    //          Initial value of $a1, otherwise
    @Override
    public String visit(EqNode node, String target) {
        String res = comparisonHelper(node,target, false);
        // generate label
        int lab = CgenEnv.getFreshLabel();
        Cgen.emitter.emitLoadBool(CgenConstants.ACC,Boolean.TRUE); // maybe not safe

        Cgen.emitter.emitBeq(CgenConstants.T1,CgenConstants.T2,lab); // do check
        Cgen.emitter.emitLoadBool(CgenConstants.A1,Boolean.FALSE);
        Cgen.emitter.emitEqualityTest();
        Cgen.emitter.emitLabelDef(lab);
        return res;
    }

    @Override
    public String visit(LEqNode node, String data) {
        String res = comparisonHelper(node,data, true);
        // generate label
        int lab = CgenEnv.getFreshLabel();
        Cgen.emitter.emitLoadBool(CgenConstants.ACC,Boolean.TRUE); // maybe not safe
        Cgen.emitter.emitBleq(CgenConstants.T1,CgenConstants.T2,lab); // do check
        Cgen.emitter.emitLoadBool(CgenConstants.ACC,Boolean.FALSE);
        Cgen.emitter.emitLabelDef(lab);
        return res;
    }

    @Override
    public String visit(LTNode node, String data) {
        String res = comparisonHelper(node,data, true);
        // generate label
        int lab = CgenEnv.getFreshLabel();
        Cgen.emitter.emitLoadBool(CgenConstants.ACC,Boolean.TRUE); // maybe not safe
        Cgen.emitter.emitBlt(CgenConstants.T1,CgenConstants.T2,lab); // do check
        Cgen.emitter.emitLoadBool(CgenConstants.ACC,Boolean.FALSE);
        Cgen.emitter.emitLabelDef(lab);

        return res;
    }

    @Override
    public String visit(NegNode node, String target) {
        String res = node.getE1().accept(this, target);
        Cgen.emitter.emitCopy();
        Cgen.emitter.emitLoad(CgenConstants.T1,3,CgenConstants.ACC); // get int value from obj
        Cgen.emitter.emitNeg(CgenConstants.T1,CgenConstants.T1); // do negation
        Cgen.emitter.emitStore(CgenConstants.T1,3,CgenConstants.ACC); // store valaue into value of a0
        return res;
    }

    @Override
    public String visit(CompNode node, String target) {
        int lab = CgenEnv.getFreshLabel();

        String r = node.getE1().accept(this, target); // load param into $a0
        Cgen.emitter.emitLoad(CgenConstants.T1,3,CgenConstants.ACC); // gets value
        Cgen.emitter.emitLoadBool(CgenConstants.ACC, Boolean.TRUE);
        Cgen.emitter.emitBeqz(CgenConstants.T1, lab);
        Cgen.emitter.emitLoadBool(CgenConstants.ACC, Boolean.FALSE);
        Cgen.emitter.emitLabelDef(lab);
        return r;
    }

    @Override
    public String visit(IntConstNode node, String target) {
        Cgen.emitter.emitLoadInt(target,node.getVal());
        return target;
    }

    @Override
    public String visit(BoolConstNode node, String target) {
        Cgen.emitter.emitLoadBool(target,node.getVal());
        return target;
    }

    @Override
    public String visit(StringConstNode node, String target) {
        Cgen.emitter.emitLoadString(target,node.getVal());
        return target;
    }

    @Override
    public String visit(IsVoidNode node, String target) {
        /*
        evaluates to true if expr is void and evaluates to false if expr is not void.
        */

        int lab = CgenEnv.getFreshLabel();
        String r = node.getE1().accept(this,target);
        Cgen.emitter.emitMove(CgenConstants.T1,CgenConstants.ACC);
        Cgen.emitter.emitLoadBool(CgenConstants.ACC,Boolean.TRUE);
        Cgen.emitter.emitBeqz(CgenConstants.T1, lab);
        Cgen.emitter.emitLoadBool(CgenConstants.ACC,Boolean.FALSE);
        Cgen.emitter.emitLabelDef(lab);


        return r;
    }

    @Override
    public String visit(ObjectNode node, String target) {
        return env.vars.lookup(node.getName()).emitRef(target);
    }

    @Override
    public String visit(NoExpressionNode node, String data) {
        Utilities.fatalError("Cgen reached no expr.\n");
        return null;
    }


    // forceDest is a wrapper for the code functions that guarantees the
    // result will go in "target".  Since the destination register is always
    // the target, there's no need for a return value.
    private void forceDest(ExpressionNode e, String target)
    {
        String r = e.accept(this, target);
        Cgen.emitter.emitMove(target, r);  //omitted if target = r.
    }


    // Helper for "e1 op e2"
    //
    // The contents of the register that holds e1 could change when
    // e2 is executed, so we need to save the result of the first computation.
    // This function:
    //   1) evaluates e1
    //   2) allocates a new var
    //   3) puts the result of e1 in that new var.
    //
    // The caller of storeOperand function should deallocate the new variable.
    private void storeOperand(Symbol temp_var, ExpressionNode e1)
    {
        //where will temp_var be allocated?
        int offset = env.getNextTempOffset();
        String dest = CgenConstants.getRegister(offset);
        if (dest == null)
        { //whoops, temp_var is going on the stack
            dest = CgenConstants.ACC;
        }
        String r_e1 = e1.accept(this,dest); //r_e1 <- e1, where hopefully r_e1=dest
        System.out.println(r_e1);
        env.addLocal(temp_var);
        env.vars.lookup(temp_var).emitUpdate(r_e1);
    }
}
