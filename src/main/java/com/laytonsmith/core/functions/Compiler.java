package com.laytonsmith.core.functions;

import com.laytonsmith.annotations.api;
import com.laytonsmith.annotations.noprofile;
import com.laytonsmith.core.*;
import com.laytonsmith.core.compiler.FileOptions;
import com.laytonsmith.core.constructs.*;
import com.laytonsmith.core.environments.Environment;
import com.laytonsmith.core.exceptions.CancelCommandException;
import com.laytonsmith.core.exceptions.ConfigCompileException;
import com.laytonsmith.core.exceptions.ConfigRuntimeException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 *
 * @author layton
 */
public class Compiler {
    public static String docs(){
        return "Compiler internal functions should be declared here. If you're reading this from anywhere"
                + " but the source code, there's a bug, because these functions shouldn't be public or used"
                + " in a script.";
    }
    
    @api
	@noprofile
    public static class p extends DummyFunction {

        public String getName() {
            return "p";
        }

        public String docs() {
            return "mixed {c...} Used internally by the compiler. You shouldn't use it.";
        }
       

        @Override
        public boolean useSpecialExec() {
            return true;
        }

        @Override
        public Construct execs(Target t, Environment env, Script parent, ParseTree... nodes) {			
            if(nodes.length == 1){
                return parent.eval(nodes[0], env);
            } else {
                return new __autoconcat__().execs(t, env, parent, nodes);
            }
        }
        public Construct exec(Target t, Environment env, Construct... args) throws ConfigRuntimeException {
            return new CVoid(t);
        }               
    }
    
    @api 
	@noprofile
	public static class centry extends DummyFunction {
        public String docs() {
            return "CEntry {label, content} Dynamically creates a CEntry. This is used internally by the "
                    + "compiler.";
        }

        public Construct exec(Target t, Environment environment, Construct... args) throws ConfigRuntimeException {
            return new CEntry(args[0], args[1], t);
        }
		
    }
    
    
    @api
	@noprofile
    public static class __autoconcat__ extends DummyFunction implements Optimizable {

        public Construct exec(Target t, Environment env, Construct... args) throws CancelCommandException, ConfigRuntimeException {
            throw new Error("Should not have gotten here, __autoconcat__ was not removed before runtime.");
        }

        public String docs() {
            return "string {var1, [var2...]} This function should only be used by the compiler, behavior"
                    + " may be undefined if it is used in code.";
        }

        @Override
		public Set<OptimizationOption> optimizationOptions() {
			return EnumSet.of(
						OptimizationOption.OPTIMIZE_DYNAMIC
			);
		}

        @Override
        public ParseTree optimizeDynamic(Target t, List<ParseTree> list) throws ConfigCompileException {
            return optimizeSpecial(t, list, true);
        }

        /**
         * __autoconcat__ has special optimization techniques needed, since it's
         * really a part of the compiler itself, and not so much a function. It
         * being a function is merely a convenience, so we can defer processing
         * until after parsing. While it is tightly coupled with the compiler,
         * this is ok, since it's really a compiler mechanism more than a
         * function.
         *
         * @param t
         * @param list
         * @return
         */
        public ParseTree optimizeSpecial(Target t, List<ParseTree> list, boolean returnSConcat) throws ConfigCompileException {
            //If any of our nodes are CSymbols, we have different behavior
            boolean inSymbolMode = false; //caching this can save Xn

            //postfix
            for (int i = 0; i < list.size(); i++) {
                ParseTree node = list.get(i);
                if (node.getData() instanceof CSymbol) {
                    inSymbolMode = true;
                }
                if (node.getData() instanceof CSymbol && ( (CSymbol) node.getData() ).isPostfix()) {
                    if (i - 1 >= 0){// && list.get(i - 1).getData() instanceof IVariable) {
                        CSymbol sy = (CSymbol) node.getData();
                        ParseTree conversion;
                        if (sy.val().equals("++")) {
                            conversion = new ParseTree(new CFunction("postinc", t), node.getFileOptions());
                        } else {
                            conversion = new ParseTree(new CFunction("postdec", t), node.getFileOptions());
                        }
                        conversion.addChild(list.get(i - 1));
                        list.set(i - 1, conversion);
                        list.remove(i);
                        i--;
                    }
                }
            }
            if (inSymbolMode) {
                try {
                    //look for unary operators
                    for (int i = 0; i < list.size() - 1; i++) {
                        ParseTree node = list.get(i);
                        if (node.getData() instanceof CSymbol && ( (CSymbol) node.getData() ).isUnary()) {
                            ParseTree conversion;
                            if (node.getData().val().equals("-") || node.getData().val().equals("+")) {
                                //These are special, because if the values to the left isn't a symbol,
                                //it's not unary
                                if ((i == 0 || list.get(i - 1).getData() instanceof CSymbol)
                                        && !(list.get(i + 1).getData() instanceof CSymbol)) {
                                    if (node.getData().val().equals("-")) {
                                        //We have to negate it
                                        conversion = new ParseTree(new CFunction("neg", t), node.getFileOptions());
                                    } else {
                                        conversion = new ParseTree(new CFunction("p", t), node.getFileOptions());
                                    }
                                } else {
                                    continue;
                                }
                            } else {
                                conversion = new ParseTree(new CFunction(( (CSymbol) node.getData() ).convert(), t), node.getFileOptions());
                            }
                            conversion.addChild(list.get(i + 1));
                            list.set(i, conversion);
                            list.remove(i + 1);
                            i--;
                        }
                    }
                    
                    for(int i = 0; i < list.size() - 1; i++){
                        ParseTree next = list.get(i + 1);
                        if(next.getData() instanceof CSymbol){
                            if(((CSymbol)next.getData()).isExponential()){
                                ParseTree conversion = new ParseTree(new CFunction(((CSymbol)next.getData()).convert(), t), next.getFileOptions());
                                conversion.addChild(list.get(i));
                                conversion.addChild(list.get(i + 2));
                                list.set(i, conversion);
                                list.remove(i + 1);
                                list.remove(i + 1);
                                i--;
                            }
                        }
                    }

                    //Multiplicative
                    for (int i = 0; i < list.size() - 1; i++) {
                        ParseTree next = list.get(i + 1);
                        if (next.getData() instanceof CSymbol) {
							CSymbol nextData = (CSymbol)next.getData();
                            if (nextData.isMultaplicative() && !nextData.isAssignment()) {
                                ParseTree conversion = new ParseTree(new CFunction(( (CSymbol) next.getData() ).convert(), t), next.getFileOptions());
                                conversion.addChild(list.get(i));
                                conversion.addChild(list.get(i + 2));
                                list.set(i, conversion);
                                list.remove(i + 1);
                                list.remove(i + 1);
                                i--;
                            }
                        }
                    }
                    //Additive
                    for (int i = 0; i < list.size() - 1; i++) {
                        ParseTree next = list.get(i + 1);
                        if (next.getData() instanceof CSymbol && ( (CSymbol) next.getData() ).isAdditive() && !((CSymbol)next.getData()).isAssignment()) {
                            ParseTree conversion = new ParseTree(new CFunction(( (CSymbol) next.getData() ).convert(), t), next.getFileOptions());
                            conversion.addChild(list.get(i));
                            conversion.addChild(list.get(i + 2));
                            list.set(i, conversion);
                            list.remove(i + 1);
                            list.remove(i + 1);
                            i--;
                        }
                    }
                    //relational
                    for (int i = 0; i < list.size() - 1; i++) {
                        ParseTree node = list.get(i + 1);
                        if (node.getData() instanceof CSymbol && ( (CSymbol) node.getData() ).isRelational()) {
                            CSymbol sy = (CSymbol) node.getData();
                            ParseTree conversion = new ParseTree(new CFunction(sy.convert(), t), node.getFileOptions());
                            conversion.addChild(list.get(i));
                            conversion.addChild(list.get(i + 2));
                            list.set(i, conversion);
                            list.remove(i + 1);
                            list.remove(i + 1);
                            i--;
                        }
                    }
                    //equality
                    for (int i = 0; i < list.size() - 1; i++) {
                        ParseTree node = list.get(i + 1);
                        if (node.getData() instanceof CSymbol && ( (CSymbol) node.getData() ).isEquality()) {
                            CSymbol sy = (CSymbol) node.getData();
                            ParseTree conversion = new ParseTree(new CFunction(sy.convert(), t), node.getFileOptions());
                            conversion.addChild(list.get(i));
                            conversion.addChild(list.get(i + 2));
                            list.set(i, conversion);
                            list.remove(i + 1);
                            list.remove(i + 1);
                            i--;
                        }
                    }
                    //logical and
                    for (int i = 0; i < list.size() - 1; i++) {
                        ParseTree node = list.get(i + 1);
                        if (node.getData() instanceof CSymbol && ( (CSymbol) node.getData() ).isLogicalAnd()) {
                            CSymbol sy = (CSymbol) node.getData();
                            ParseTree conversion = new ParseTree(new CFunction(sy.convert(), t), node.getFileOptions());
                            conversion.addChild(list.get(i));
                            conversion.addChild(list.get(i + 2));
                            list.set(i, conversion);
                            list.remove(i + 1);
                            list.remove(i + 1);
                            i--;
                        }
                    }
                    //logical or
                    for (int i = 0; i < list.size() - 1; i++) {
                        ParseTree node = list.get(i + 1);
                        if (node.getData() instanceof CSymbol && ( (CSymbol) node.getData() ).isLogicalOr()) {
                            CSymbol sy = (CSymbol) node.getData();
                            ParseTree conversion = new ParseTree(new CFunction(sy.convert(), t), node.getFileOptions());
                            conversion.addChild(list.get(i));
                            conversion.addChild(list.get(i + 2));
                            list.set(i, conversion);
                            list.remove(i + 1);
                            list.remove(i + 1);
                            i--;
                        }
                    }
					//Assignment
					for(int i = 0; i < list.size() - 1; i++){
						ParseTree node = list.get(i + 1);
						if(node.getData() instanceof CSymbol && ((CSymbol)node.getData()).isAssignment()){
							CSymbol sy = (CSymbol) node.getData();
							String conversionFunction = sy.convertAssignment();
							ParseTree lhs = list.get(i);
							if(conversionFunction != null){
								ParseTree conversion = new ParseTree(new CFunction(conversionFunction, t), node.getFileOptions());
								//grab the right side, and turn it into an operation with the left side
								try{
									ParseTree rhs = list.get(i + 2);
									conversion.addChild(lhs);
									conversion.addChild(rhs);
									list.set(i + 2, conversion);
								} catch(IndexOutOfBoundsException e){
									throw new ConfigCompileException("Invalid symbol listed", t);
								}
							}
							//Simple assignment now
							ParseTree assign = new ParseTree(new CFunction("assign", t), node.getFileOptions());
							ParseTree rhs = list.get(i + 2);
							assign.addChild(lhs);
							assign.addChild(rhs);
							list.set(i, assign);
							list.remove(i + 1);
							list.remove(i + 1);
							i--;
						}
					}
                }
                catch (IndexOutOfBoundsException e) {
                    throw new ConfigCompileException("Unexpected symbol (" + list.get(list.size() - 1).getData().val() + "). Did you forget to quote your symbols?", t);
                }
            }

            //Look for a CEntry here
            if (list.size() >= 1) {
                ParseTree node = list.get(0);
                if (node.getData() instanceof CLabel) {
                    ParseTree value = new ParseTree(new CFunction("__autoconcat__", t), node.getFileOptions());
                    for (int i = 1; i < list.size(); i++) {
                        value.addChild(list.get(i));
                    }
                    ParseTree ce = new ParseTree(new CFunction("centry", t), node.getFileOptions());
                    ce.addChild(node);
                    ce.addChild(value);
                    return ce;
                }
            }

            //We've eliminated the need for __autoconcat__ either way, however, if there are still arguments
            //left, it needs to go to sconcat, which MAY be able to be further optimized, but that will
            //be handled in MethodScriptCompiler's optimize function. Also, we must scan for CPreIdentifiers,
            //which may be turned into a function
            if (list.size() == 1) {
                return list.get(0);
            } else {
                for(int i = 0; i < list.size(); i++){
                    if(list.get(i).getData().getCType() == Construct.ConstructType.IDENTIFIER){
                        if(i == 0){
                            //Yup, it's an identifier
                            CFunction identifier = new CFunction(list.get(i).getData().val(), t);
                            list.remove(0);
                            ParseTree child = list.get(0);
                            if(list.size() > 1){
                                child = new ParseTree(new CFunction("sconcat", t), child.getFileOptions());
                                child.setChildren(list);
                            }
                            try{
                                Function f = (Function)FunctionList.getFunction(identifier);                                
                                ParseTree node 
                                        = new ParseTree(f.execs(t, null, null, child), child.getFileOptions());                                
                                return node;
                            } catch(Exception e){
                                throw new Error("Unknown function " + identifier.val() + "?");
                            }                                                      
                        } else {
                            //Hmm, this is weird. I'm not sure what condition this can happen in
                            throw new ConfigCompileException("Unexpected IDENTIFIER? O.o Please report a bug,"
                                    + " and include the script you used to get this error.", t);
                        }
                    }
                }
                ParseTree tree;
				FileOptions options = new FileOptions(new HashMap<String, String>());
				if(!list.isEmpty()){
					options = list.get(0).getFileOptions();
				}
                if (returnSConcat) {
                    tree = new ParseTree(new CFunction("sconcat", t), options);
                } else {
                    tree = new ParseTree(new CFunction("concat", t), options);
                }
                tree.setChildren(list);
                return tree;
            }
        }
                
    }
    
    @api
    public static class npe extends DummyFunction {

        public Integer[] numArgs() {
            return new Integer[]{0};
        }

        public boolean isRestricted() {
            return true;
        }

        public Construct exec(Target t, Environment env, Construct... args) throws ConfigRuntimeException {
            Object o = null;
            o.toString();
            return new CVoid(t);
		}
                
    }
    
    @api 
	@noprofile
	public static class dyn extends DummyFunction{

        public String docs() {
            return "exception {[argument]} Registers as a dynamic component, for optimization testing; that is"
                    + " to say, this will not be optimizable ever."
                    + " It simply returns the argument provided, or void if none.";
        }

        public Construct exec(Target t, Environment environment, Construct... args) throws ConfigRuntimeException {
            if(args.length == 0){
                return new CVoid(t);
            }
            return args[0];
        }
                       
    }
	
	@api public static class __cbracket__ extends DummyFunction implements Optimizable {

		public Construct exec(Target t, Environment environment, Construct... args) throws ConfigRuntimeException {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public Set<OptimizationOption> optimizationOptions() {
			return EnumSet.of(
						OptimizationOption.OPTIMIZE_DYNAMIC
			);
		}

		@Override
		public ParseTree optimizeDynamic(Target t, List<ParseTree> children) throws ConfigCompileException, ConfigRuntimeException {
			FileOptions options = new FileOptions(new HashMap<String, String>());
			if(!children.isEmpty()){
				options = children.get(0).getFileOptions();
			}
			ParseTree node;
			if(children.isEmpty()){
				node = new ParseTree(new CVoid(t), options);
			} else if(children.size() == 1){
				node = children.get(0);
			} else {
				//This shouldn't happen. If it does, it means that the autoconcat didn't already run.
				throw new ConfigCompileException("Unexpected children. This appears to be an error, as __autoconcat__ should have already been processed. Please"
						+ " report this error to the developer.", t);
			}
			return new ParseTree(new CBracket(node), options);
		}
		
		
		
	}
	
	@api public static class __cbrace__ extends DummyFunction implements Optimizable {

		public Construct exec(Target t, Environment environment, Construct... args) throws ConfigRuntimeException {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public Set<OptimizationOption> optimizationOptions() {
			return EnumSet.of(
						OptimizationOption.OPTIMIZE_DYNAMIC
			);
		}

		@Override
		public ParseTree optimizeDynamic(Target t, List<ParseTree> children) throws ConfigCompileException, ConfigRuntimeException {
			FileOptions options = new FileOptions(new HashMap<String, String>());
			if(!children.isEmpty()){
				options = children.get(0).getFileOptions();
			}
			ParseTree node;
			if(children.isEmpty()){
				node = new ParseTree(new CVoid(t), options);
			} else if(children.size() == 1){
				node = children.get(0);
			} else {
				//This shouldn't happen. If it does, it means that the autoconcat didn't already run.
				throw new ConfigCompileException("Unexpected children. This appears to be an error, as __autoconcat__ should have already been processed. Please"
						+ " report this error to the developer.", t);
			}
			return new ParseTree(new CBrace(node), options);
		}				
		
	}
}
