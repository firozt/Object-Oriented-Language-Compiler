import ast.ClassNode;
import ast.ProgramNode;
import ast.Symbol;

import java.util.ArrayList;

class Semant {

    public static ClassTable classTable;
    public static SymbolTable<Pair<Symbol, Kind>> symtable = new SymbolTable();
    public static Symbol filename;

    public static void analyze(ProgramNode program) {
        ArrayList<ClassNode> cls =(ArrayList<ClassNode>) (new ArrayList<>(program.getClasses()).clone());
        classTable = new ClassTable(cls);

        ScopeCheckingVisitor scopecheckVisitor = new ScopeCheckingVisitor();
        program.accept(scopecheckVisitor, null);
        TypeCheckingVisitor typecheckVisitor = new TypeCheckingVisitor();
        program.accept(typecheckVisitor, null);

        if (Utilities.errors()) {
            Utilities.fatalError(Utilities.ErrorCode.ERROR_SEMANT);
        }
    }

}
// -------------- Helper Classes -------------- //


/*
    generic java class that holds a pair of objects the class is
    immutable which allows it to be hashed to be the key in a hashtable
*/
final class Pair<T,V>{
    final public T first;
    final public V second;

    Pair(T first, V second) {
        this.first = first;
        this.second = second;
    }
    public Symbol getSymbol() {
        return StringTable.idtable.addString(
                first.toString() + second.toString()
        );
    }
}

enum Kind {
    VAR,
    METHOD,

}