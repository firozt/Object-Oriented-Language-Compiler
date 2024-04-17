import ast.Symbol;

import java.util.ArrayList;

class CgenEmitDispatchTables extends CgenVisitor {

    // Emit the dispatch table of a class
    @Override
    Void visit(CgenNode v) {
        Cgen.emitter.codeDispatchTables(v.env);
        super.visit(v);

        return null;
    }
}
