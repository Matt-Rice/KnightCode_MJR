/**
* Class that overrides some of the methods of the ANTLR generated base listener that will be responsible for performing bytecode operations for the grammar rules
* @author Matt Rice
* @version 1.0
* Assignment 5
* CS322 - Compiler Construction
* Spring 2024
**/
package compiler;

import lexparse.*;
import org.antlr.v4.runtime.ParserRuleContext; // need to debug every rule
import org.antlr.v4.runtime.tree.TerminalNode;
//Explicit import for ASM bytecode constants
import org.objectweb.asm.*;  //classes for generating bytecode
import compiler.utils.*;
import java.util.*;
import java.lang.*;

public class MyBaseListener extends KnightCodeBaseListener{

    private ClassWriter cw;  //ClassWriter for a KnightCode class
	private MethodVisitor mainVisitor; //global MethodVisitor
	private String programName; //name of the output file
    private Map<String, Variable> symbolTable; //map that will store the name of the variable along with its corresponding Variable object which will contain some of its attributes
    private int memoryPointer;
    private Stack<Integer> condManager; //Manages

    /**
     * Constructor for MyBaseListener
     * @param programName the name of the program
     */
    public MyBaseListener(String programName){
        this.programName = programName;
        
    }//end constructor

    /**
     * Method that removes the first and last characters of a string (Will be used to remove quotes around Strings when printing)
     * @param s the string that will be modified
     * @return the string without the first and last characters
     */
    public String removeFirstandLast(String s){
        return s.substring(1, s.length() -1);
    }//end removeFirstandLast

    /**
     * Method that will set up the ClassWriter and create the constructor
     * @param name the name of the program that will be created
     */
    public void beginClass(String name){
        
        // Set up the classwriter
		cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,programName, null, "java/lang/Object",null);
        
        // Creating Constructor for the class
        {
			MethodVisitor mv=cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0); //load the first local variable: this
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V",false);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(1,1);
			mv.visitEnd();
		}

    }//end beginClass

    /**
     * Ends the main method and writes the ClassWriter data into the outputFile
     */
    public void closeClass(){

            mainVisitor.visitInsn(Opcodes.RETURN);
            mainVisitor.visitMaxs(0, 0);
            mainVisitor.visitEnd();
    
            cw.visitEnd();
    
                byte[] b = cw.toByteArray();
    
                Utilities.writeFile(b,this.programName+".class");
    
            System.out.println("\n\n\nCompiling Finished");
        
    }//end closeClass

    @Override
    /**
     * Begins the KnightCode class and is triggered once file is entered 
     */
    public void enterFile(KnightCodeParser.FileContext ctx){
        System.out.println("Entering File");

        programName = ctx.ID().getText();
    

        beginClass(programName);
    }//end enterFile

    @Override
    /**
     * Closes the KnightCode class triggered once the end of the program is reached
     */
    public void exitFile(KnightCodeParser.FileContext ctx){
        
        closeClass();

        System.out.println("Exiting File");
    }//end exitFile

    @Override
    // triggered once declare is reached
    /**
     * Once Declare is entered, a HashMap for the symbol table will be initialized and the stack memory pointer will be set to zero
     */
    public void enterDeclare(KnightCodeParser.DeclareContext ctx){
        //Debug
        System.out.println("Enter Declare");
        
        symbolTable = new HashMap<>();
        memoryPointer = 0;
    }//end enterDeclare

    @Override
    public void exitDeclare(KnightCodeParser.DeclareContext ctx){
        System.out.println("SymbolTable");
        for (Map.Entry<String, Variable> entry : symbolTable.entrySet()){
            System.out.println("Key: " + entry.getKey() + " Var: " + entry.getValue().toString());
        }
    }

    @Override
    /**
     * Once variable is entered, the name and type will be used to instantiate a new Variable object using the attributes from the declaration and put it into the symbol table
     */
    public void enterVariable(KnightCodeParser.VariableContext ctx){
        //Debug
        System.out.println("Enter Variable");
        
        String type = ctx.vartype().getText();

        // Check if declared type is unsupported
        if (!type.equals("INTEGER") && !type.equals("STRING")){
            System.err.println("Compilation error: the entered type is not supported.");
            System.exit(1);
        }

        String name = ctx.identifier().getText();
        Variable v = new Variable(name, type, memoryPointer++);
        symbolTable.put(name, v);
    }//end enterVariable

    @Override
    /**
     * Triggers when body is entered and initializes the main method
     */
    public void enterBody(KnightCodeParser.BodyContext ctx){
        //Debug
        System.out.println("Enter Body");
        
        // Start MethodVisitor for main method
        
        mainVisitor=cw.visitMethod(Opcodes.ACC_PUBLIC+Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mainVisitor.visitCode();
    }//end enterBody
    
    public void evalExpr(KnightCodeParser.ExprContext ctx){
        
        // If the expr is just a number reads and parses the text as an int and loads it to constant pool
        if (ctx instanceof KnightCodeParser.NumberContext){
            int value = Integer.parseInt(ctx.getText());
            
            //debug
            System.out.println(value + "Is on stack");
            mainVisitor.visitLdcInsn(value);
        }//number

        // If the expr is an identifier
        else if (ctx instanceof KnightCodeParser.IdContext){
            String id = ctx.getText();
            Variable var = symbolTable.get(id);
            
            //debug
            System.out.println("expr id " + id + "\nvar: " + var.toString());

            // If type of the variable is INTEGER
            if (var.getType().equals("INTEGER")){
                mainVisitor.visitVarInsn(Opcodes.ILOAD, var.getLocation());
                System.out.println(id+ "is on stack");
            }

            else if (var.getType().equals("STRING")){
                mainVisitor.visitVarInsn(Opcodes.ALOAD, var.getLocation());
            } 
            
        }//id   
        else if (ctx instanceof KnightCodeParser.SubtractionContext){
            
            for(KnightCodeParser.ExprContext expr : ((KnightCodeParser.SubtractionContext)ctx).expr()){
                evalExpr(expr);
            }//for
        System.out.println("subbing");
        mainVisitor.visitInsn(Opcodes.ISUB);
            
        }
        else if (ctx instanceof KnightCodeParser.AdditionContext){
            
            for(KnightCodeParser.ExprContext expr : ((KnightCodeParser.AdditionContext)ctx).expr()){
                evalExpr(expr);
            }//for
        System.out.println("adding");
        mainVisitor.visitInsn(Opcodes.IADD);
            
        }
        else if (ctx instanceof KnightCodeParser.MultiplicationContext){
            
            for(KnightCodeParser.ExprContext expr : ((KnightCodeParser.MultiplicationContext)ctx).expr()){
                evalExpr(expr);
            }//for
        System.out.println("Multiplying");
        mainVisitor.visitInsn(Opcodes.IMUL);
            
        }
        //Division
        else if (ctx instanceof KnightCodeParser.DivisionContext){
            
            for(KnightCodeParser.ExprContext expr : ((KnightCodeParser.DivisionContext)ctx).expr()){
                evalExpr(expr);
            }//for
        System.out.println("dividing");
        mainVisitor.visitInsn(Opcodes.IDIV);
            
        }

        
    }//end evalExpr

    @Override

    public void enterComparison(KnightCodeParser.ComparisonContext ctx){
        
        String op = ctx.comp().getText();

        Label trueLabel = new Label();
        Label endLabel = new Label();

        evalExpr(ctx.expr(0));
        evalExpr(ctx.expr(1));

        switch (op) {
            case "GT":
                mainVisitor.visitJumpInsn(Opcodes.IF_ICMPGT, trueLabel);
                break;
        
            case "LT":
                mainVisitor.visitJumpInsn(Opcodes.IF_ICMPLT, trueLabel);
                break;

            case "EQ":
                mainVisitor.visitJumpInsn(Opcodes.IF_ICMPEQ, trueLabel);
                break;
            case "NEQ":
            mainVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, trueLabel);
                break;
        }

        //If not true, load 0 and jump to end
        mainVisitor.visitLdcInsn(0);
        mainVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);

        //If true load 1
        mainVisitor.visitLabel(trueLabel);
        mainVisitor.visitLdcInsn(1);

        mainVisitor.visitLabel(endLabel);

    }

    /**
     * Method that will check if a string is either a number or an identifier in the symbol table and will load it accordingly
     * @param operand the string with the ID or value to be loaded
     */
    public void loadInteger(String operand){
        int location;
        
        //If the string is a key of the symbol table ("It's the ID of a variable")
        if (symbolTable.containsKey(operand)){
            Variable var = symbolTable.get(operand);
            location = var.getLocation();
            mainVisitor.visitVarInsn(Opcodes.ILOAD, location);
        }
        //If it's a number
        else {
            mainVisitor.visitLdcInsn(Integer.parseInt(operand));
        }
    }//end loadInteger
    
    @Override
    public void enterDecision(KnightCodeParser.DecisionContext ctx){
        Label trueLabel = new Label();
        Label endLabel = new Label();
        
        
        //Load the children to be compared
        String num1 = ctx.getChild(1).getText();
        String num2 = ctx.getChild(3).getText();

        loadInteger(num1);
        loadInteger(num2);

        //Decide which comparison to use
        String op = ctx.comp().getText();
        
        switch (op) {
            case "GT":
                mainVisitor.visitJumpInsn(Opcodes.IF_ICMPGT, trueLabel);
                break;
        
            case "LT":
                mainVisitor.visitJumpInsn(Opcodes.IF_ICMPLT, trueLabel);
                break;

            case "EQ":
                mainVisitor.visitJumpInsn(Opcodes.IF_ICMPEQ, trueLabel);
                break;
            case "NEQ":
            mainVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, trueLabel);
                break;
        }
        //Else executes here
        
        
        mainVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);


        // Go here when comparison is true
        mainVisitor.visitLabel(trueLabel);
        
        
        //End label
        mainVisitor.visitLabel(endLabel);

    }

    @Override
    /**
     * Is triggered when Setvar is entered and will define a previously declared variable
     */
    public void enterSetvar(KnightCodeParser.SetvarContext ctx){
    
        String varName = ctx.ID().getText(); 

        //Debug
        System.out.println("Enter SetVar: " + varName);

        Variable var = symbolTable.get(varName);
        
        //Need to evaluate EXPR before setting stuff
        //Make a method that takes in expr context and checks for operators and such

        // If the variable was not previously declared
        // May do error handling in the future
        if (var == null){
            System.err.println(varName + " has not been declared yet");
            System.exit(1);
        }
        evalExpr(ctx.expr());

        //Defines variable if it is an INTEGER
        if (var.getType().equals("INTEGER")){
            System.out.println("Storing for " + varName);
            mainVisitor.visitVarInsn(Opcodes.ISTORE, var.getLocation());
        }
        
        //Defines variable if it is an STRING
        else if (var.getType().equals("STRING")){
            mainVisitor.visitVarInsn(Opcodes.ASTORE, var.getLocation());
        }
        System.out.println("SymbolTable");
        for (Map.Entry<String, Variable> entry : symbolTable.entrySet()){
            System.out.println("Key: " + entry.getKey() + " Var: " + entry.getValue().toString());
        }

    }//end enterSetvar

    @Override
    public void exitSetvar(KnightCodeParser.SetvarContext ctx){
        System.out.println("Exiting setVar\nSymbolTable");
        for (Map.Entry<String, Variable> entry : symbolTable.entrySet()){
            System.out.println("Key: " + entry.getKey() + " Var: " + entry.getValue().toString());
        }
    }
    @Override
    /**
     * Is triggered whenever print is encountered and will either print out the value of the identifier specified, or a string that is specified
     */
    public void enterPrint(KnightCodeParser.PrintContext ctx){
        //Debug
        System.out.println("Enter Print");
        
        mainVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

        // If the subject of the printing is an ID then it searches and finds its stack location so it can be loaded to be printed
        if(ctx.ID() != null){   
            String varID = ctx.ID().getText();
            Variable var = symbolTable.get(varID);
            int location = var.getLocation(); //location of the variable

            if (var.getType().equals("INTEGER")){
                mainVisitor.visitVarInsn(Opcodes.ILOAD, location);
                mainVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
            }
            else{
                mainVisitor.visitVarInsn(Opcodes.ALOAD, location);
                mainVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            }
        }
        //If the subject is a String, it will load the string to the constant pool
        else if(ctx.STRING()!=null){
            String str = removeFirstandLast(ctx.STRING().getText());
            mainVisitor.visitLdcInsn(str);
            mainVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }
    }//end enterPrint

    
    @Override
    /**
     * Method that will read an input from the user and store it in the variable whose identifier follows the read call 
     */
    public void enterRead(KnightCodeParser.ReadContext ctx){
        //debug
        System.out.println("Entering Read");
        
        //Initializes the variable that will store the value inputted by the user
        Variable var = symbolTable.get(ctx.ID().getText());
        int scanLocation = memoryPointer++;

        // Initializes the Scanner object
        mainVisitor.visitTypeInsn(Opcodes.NEW, "java/util/Scanner"); // Creates Scanner and pushes it to the stack
        mainVisitor.visitInsn(Opcodes.DUP); // Duplicates the Scanner reference which will be used in initializing and storing the scanner
        mainVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;"); // System.in
        mainVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false); // Initializes Scanner
        mainVisitor.visitVarInsn(Opcodes.ASTORE, scanLocation); // Stores Scanner

        //Handles if variable is of type int
        if (var.getType().equals("INTEGER")){
            // Prompts the user to enter an integer
            mainVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mainVisitor.visitLdcInsn("Please enter an integer value for " + ctx.ID().getText() + ": ");  
            mainVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

            // Read integer input from the user
            mainVisitor.visitVarInsn(Opcodes.ALOAD, scanLocation); // Loads scanner
            mainVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Scanner", "nextInt", "()I", false); // Scan.nextLong()
            mainVisitor.visitVarInsn(Opcodes.ISTORE, var.getLocation()); // Store the int value in a variable
        }
        
        //Handles if variable is of type String
        else if (var.getType().equals("STRING")){
            // Prompts the user to enter a String
            mainVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mainVisitor.visitLdcInsn("Please enter a String value for " + ctx.ID().getText() + ": ");  
            mainVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

            // Read integer input from the user
            mainVisitor.visitVarInsn(Opcodes.ALOAD, scanLocation); // Loads scanner
            mainVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false); // Scan.nextLong()
            mainVisitor.visitVarInsn(Opcodes.ASTORE, var.getLocation()); // Store the int value in a variable
        }


    }//end enterRead
    
    

    
}//end MyBaseListener
 