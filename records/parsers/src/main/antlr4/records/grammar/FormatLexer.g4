lexer grammar FormatLexer;

import StringLexerShared;

DIGITS : [0-9]+;

UNIT : '{' ~'}'* '}';

BOOLEAN : 'Boolean';
NUMBER : 'Number';
TEXT : 'Text';
YEARMONTHDAY : 'Date';
YEARMONTH : 'YEARMONTH';
TIMEOFDAY : 'TIMEOFDAY';
TIMEOFDAYZONED : 'TIMEOFDAYZONED';
DATETIME : 'DATETIME';
DATETIMEZONED : 'DATETIMEZONED';
TAGGED : 'TAGGED';
TYPE : 'TYPE';
TYPEVAR : 'TYPEVAR';
SPACE_KWD : 'SPACE';
ZERO_KWD : 'ZERO';
DEFAULT : 'DEFAULT' -> pushMode(VALUE_MODE);

WS : ( ' ' | '\t' )+ -> skip;

COLUMN : 'COLUMN';

CONS: ':';
UNQUOTED_NAME : ~[ \t\r\n:(),\[\]|"-{}]+;
OPEN_BRACKET : '(';
CLOSE_BRACKET : ')';
COMMA: ',';
OPEN_SQUARE : '[';
CLOSE_SQUARE : ']';
DASH: '-';
TAGOR: '|';

INCOMPLETE : '@INCOMPLETE';
INVALIDOPS : '@INVALIDOPS';


NEWLINE : '\r'? '\n' ;

mode VALUE_MODE;
VALUE_END: NEWLINE -> popMode;
VALUE: (~[\n\r])+;
