//
// JlSiteswap.g4
//
// This is a grammar definition file for use with the ANTLR4 parser generator.
// This defines the version of siteswap juggling notation that is recognized
// by Juggling Lab.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

grammar JlSiteswap;

pattern :
    ( groupedpattern
    | solosequence
    | passingsequence
    | WILDCARD
    )+ SWITCHREVERSE?
    ;

WILDCARD: '?' ;

SWITCHREVERSE: '*';

groupedpattern : '(' pattern '^' number ')' ;

solosequence :
    ( solomultithrow
    | solopairedthrow '!'? SPC*
    | solohandspecifier SPC*
    )+ ;

solohandspecifier: 'L' | 'R' ;

solopairedthrow : '(' SPC* solomultithrow ',' SPC* solomultithrow ')' ;

solomultithrow :
      solosinglethrow SPC*                   #solomultisingle
    | '[' SPC* (solosinglethrow SPC*)+ ']'   #solomultibraces
    ;

solosinglethrow: throwvalue 'x'? modifier? '/'? ;

passingsequence : passinggroup+ ;

passinggroup : '<' SPC* passingthrows ('|' SPC* passingthrows)* '>' SPC* ;

passingthrows :
    ( passingmultithrow
    | passingpairedthrow '!'? SPC*
    | passinghandspecifier SPC*
    )+
    ;

passingpairedthrow : '(' SPC* passingmultithrow ',' SPC* passingmultithrow ')' ;

passingmultithrow :
      passingsinglethrow SPC*                  #passmultisingle
    | '[' SPC* (passingsinglethrow SPC*)+ ']'  #passmultibraces
    ;

passingsinglethrow: throwvalue 'x'? ('p' number?)? modifier? '/'?;

passinghandspecifier: 'L' | 'R' ;

throwvalue :
      '{' SPC* number SPC* '}'  #bracevalue
    | DIGIT                     #digitvalue
    | LETTER                    #lettervalue
    | 'x'                       #xvalue
    | 'p'                       #pvalue
    ;

number : DIGIT+;

modifier: MOD (MOD | 'L' | 'R' )* ;

MOD : [A-KM-QS-Z] ;

SPC : ' ' ;

LETTER : [a-qr-wyz] ;

DIGIT : [0-9] ;

WHITESPACE: [\t\r\n] -> skip;
