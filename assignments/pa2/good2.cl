-- class Main inherits IO{
--  x:Bool;
--  main(): Object {{ 
--                     ~7;
--                     ~foo();
--                 }};
--  foo(): Int {4};
-- };
class Main inherits IO{
 attr: B;
 main(): SELF_TYPE {
  attr.foo()
 };
--  foo(x: Int) : Int {x+3};
};
class B {
 foo() : SELF_TYPE {
    self
 };
};