class Main inherits IO {
   stack: Stack <- new Stack;
   conversion : A2I <- new A2I;

   main() : Object {{
      start();
   }};
   
   start() : Object {
      let userInput : String <- in_string() in
      if userInput="x" then {
         -- Exists recursion gracefully
         0;
      }
      else {
         -- Handle input accordingly
         checkInput(userInput);
         -- Recurse
         start();
      }
      fi
   };

-- Input is not a terminating input, see what to do
   checkInput(userInput: String) : Object {
      if userInput = "d" then display(stack.peek())
      else if userInput = "e" then eval()
      else stack.add(userInput) 
      fi
      fi
   };

   eval() : Object {
      let topVal : String <- stack.peek().getVal() in 
      if topVal = "+" then addCommand()
      else if topVal = "s" then swap()
      else 0 -- integer at top, do nothing
      fi
      fi
   };

   addCommand() : Object {
      let firstVal : Int <- conversion.a2i(stack.peek().getNext().getVal()) in
      let secondVal : Int <- conversion.a2i(stack.peek().getNext().getNext().getVal()) in
      let sum : Int <- firstVal + secondVal in
      {
         stack.pop(); -- pops +
         stack.pop(); -- pops first val
         stack.pop(); -- pops second val
         stack.add(conversion.i2a(sum));
      }
   };


-- Recursivly prints stack
   display(node: Node) : Object {
      -- Base case, last node in the linked list
      if node.getVal() = "" then { 
         0;
      }
      else {
         out_string(node.getVal().concat("\n"));
         display(node.getNext());
      }
      fi
   };

-- Swaps the value of last two nodes, not the node itself
-- Done this way for simplicity (i dont want to deal with pointers ;) )
   swap(): Object {
      let head: Node <- stack.peek() in -- + node
      let value1 : String <- head.getNext().getVal() in
      let value2 : String <- head.getNext().getNext().getVal() in
      {
         stack.pop();
         head.getNext().setVal(value2);
         head.getNext().getNext().setVal(value1); 
      }
      
   };
};

-- Stack implementation using a singly linked list
-- Top represents the current top of the stack
class Stack {
   top : Node <- new Node;

-- Creates a new node, sets that nodes next attribute to the current top
-- then moves the top pointer to point at the newly created top
   add(nextVal: String) : Stack {
      let newNode : Node <- createNode(nextVal) in {
         newNode.setNext(top);
         top <- newNode;
         self;
      }
   };

   pop() : Stack {{
      top <- top.getNext();
      self;
   }};

   createNode(val: String) : Node {
      let newNode : Node <- new Node in {
         newNode.setVal(val);
      }
   };

   peek() : Node {
      top
   };

};

-- Simple node class, holds a string value and a pointer to a next node
class Node {
   val : String;
   next: Node;

   setVal(newVal : String) : Node {{
      val <- newVal;
      self;
   }};

   setNext(nextNode : Node) : Node {{
      next <- nextNode;
      self;
   }};

   getNext() : Node {
      next
   };

   getVal() : String {
      val
   };
};

-- coolc stack.cl atoi.cl
-- coolspim -file stack.s 