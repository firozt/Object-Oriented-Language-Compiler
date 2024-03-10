/**
 * Define a grammar for Cool
 */
parser grammar CoolParser;

options { tokenVocab = CoolLexer; }


/*  Starting point for parsing a Cool file  */

program 
	: (coolClass SEMICOLON)+ EOF
	| CLASS TYPE
	| expr
	;

coolClass
    : CLASS TYPE (INHERITS TYPE)? CURLY_OPEN (feature SEMICOLON)* CURLY_CLOSE

	;
feature
    : ID PARENT_OPEN (formal (COMMA formal)*)? PARENT_CLOSE COLON TYPE CURLY_OPEN expr CURLY_CLOSE
    | ID COLON TYPE (ASSIGN_OPERATOR expr)?
    ;

formal
    : ID COLON TYPE
    ;

expr
    : ID ASSIGN_OPERATOR expr
    | expr (AT TYPE)? PERIOD ID PARENT_OPEN (expr (COMMA expr)*)? PARENT_CLOSE
    | ID PARENT_OPEN (expr (COMMA expr)*)? PARENT_CLOSE
    | IF expr THEN expr ELSE expr FI
    | WHILE expr LOOP expr POOL
    | CURLY_OPEN (expr SEMICOLON)* CURLY_CLOSE
    | LET ID COLON TYPE (ASSIGN_OPERATOR expr)? (COMMA ID COLON TYPE (ASSIGN_OPERATOR expr)?)* IN expr // TODO
    | CASE expr OF (ID COLON TYPE RIGHTARROW expr SEMICOLON)+ ESAC
    | NEW TYPE
    | ISVOID expr
    |<assoc=left> expr PLUS_OPERATOR expr
    |<assoc=left> expr MINUS_OPERATOR expr
    |<assoc=left>expr MULT_OPERATOR expr
    |<assoc=left> expr DIV_OPERATOR expr
    | INT_COMPLEMENT_OPERATOR expr
    | expr LESS_OPERATOR expr
    | expr LESS_EQ_OPERATOR expr
    | expr EQ_OPERATOR expr
    | NOT expr
    | PARENT_OPEN expr PARENT_CLOSE
    | ID
    | INT_CONST
    | STR_CONST
    | BOOL_TRUE
    | BOOL_FALSE
    ;
