/**
 * Define a lexer rules for Cool
 */
lexer grammar CoolLexer;

/* Punctution */

PERIOD              : '.';
COMMA               : ',';
AT                  : '@';
SEMICOLON           : ';';
COLON               : ':';

CURLY_OPEN          : '{' ;
CURLY_CLOSE         : '}' ;
PARENT_OPEN         : '(' ;
PARENT_CLOSE        : ')' ;

/* Operators */

PLUS_OPERATOR       : '+';
MINUS_OPERATOR      : '-';
MULT_OPERATOR       : '*';
DIV_OPERATOR        : '/';

INT_COMPLEMENT_OPERATOR     : '~';

LESS_OPERATOR               : '<';
LESS_EQ_OPERATOR            : '<=';
EQ_OPERATOR                 : '=' ;
ASSIGN_OPERATOR 	        : '<-';
RIGHTARROW                  : '=>';


/* Keywords */
LET : 'let';
ISVOID : 'isvoid';
CLASS : [cC][lL][aA][sS][sS];
NEW : 'new';
NOT : 'not';
CASE : 'case';
ESAC : 'esac';
OF : 'of';
IF : 'if';
FI : 'fi';
WHILE : 'while';
LOOP : 'loop';
POOL : 'pool';
BOOL_TRUE : 'true';
BOOL_FALSE : 'false';
INHERITS : 'inherits';
THEN : 'then';
ELSE : 'else';
IN : 'in';

fragment DIGIT : [0-9];
fragment UPPERCASE : [A-Z];
fragment LOWERCASE : [a-z] ;

INT_CONST : DIGIT+;
ID : LOWERCASE (LOWERCASE | UPPERCASE | DIGIT | '_')*;
TYPE : UPPERCASE (LOWERCASE | UPPERCASE | DIGIT | '_' )*;


BEGIN_STRING : '"' -> skip, pushMode(STRING_MODE);
LINE_COMMENT : '--' ~[\r\n]* -> skip;

BEGIN_COMMENT : '(*' -> skip , pushMode(COMMENT_MODE);
UNMATCHED_COMMENT : '*)' {setText("Unmatched *)");} -> type(ERROR);

WHITESPACE : (' ' | '\n' | '\t' | '\r' | '\u000b')+ -> skip;
ERROR : . ;

mode COMMENT_MODE;
END_COMMENT : '*)' -> skip, popMode;
BEGIN_INNER : '(*' -> skip, pushMode(COMMENT_MODE);
COMMENT_TEXT : . -> skip;
//NO_MATCH : (?!.*\*\)).*? EOF {setText("EOF in comment");}-> type(ERROR) ;

mode STRING_MODE;
fragment STRING_ESCAPE : '\\' [trn"'\\];

END_STRING : '"' -> skip, popMode;
STR_NULL : STR_CONST* '\u0000' STR_CONST* {setText("String contains null character.");} -> type(ERROR), pushMode(FIND_END_STRING);
STR_ESCAPED_NULL : STR_CONST* '\\' '\u0000'  {setText("String contains escaped null character.");} STR_CONST* -> type(ERROR);
STR_CONST : (~[\\\r\n"]|STRING_ESCAPE)+;
VALID_NEWLINE : '\\' '\n' -> skip;
UNTERMINATED_STRING : '\n' {setText("Unterminated string constant");} -> type(ERROR), popMode;
EOF_IN_STRING : STR_CONST*? EOF {setText("EOF in string constant");} -> type(ERROR), popMode;

mode FIND_END_STRING;
SKIP_STRING: ~["\n] -> skip;
END : ["\n] -> skip, popMode, popMode;