// This is a Ghidra plugin for deobfuscating.
//
// Usage:
// ADD /src into Ghidra scripts directory list and refreshes it.
//
// Before deobfuscation process, you need to create a JSON file including necessary information. In the future, json
// files may be generated automatically and user just need to check it.
//
// This plugin now support several kinds of deobfuscation:
// - Control Flow Flattening with local variables
//
// The JSON file needs to be deserialized into `SolverConfig` class, here is its format:
//
//```json
//{
//  "target_local_vars": [
//    {
//      "var_size": 4,
//      "var_init_address": "400800"
//    },
//    {
//      "var_size": 8,
//      "var_init_address": 4196768
//    }
//  ]
//}
//```
//
// - target_local_vars: configs of Control Flow Flattening with local variables (called state variable).
//   - var_size: variable size, only can be 4 or 8.
//   - var_init_address: memory address containing the instruction that initializes the state variable. It can be a hex
//     string (no "0x") or an integer.
//
// After creating your json file, you can run this plugin in Ghidra GUI and choose your json file. Then just wait the
// miracle take place.
//
//@author Hornos - Hornos3.github.com, hornos@hust.edu.cn
//@category Binary

import com.google.gson.*;
import docking.options.OptionsService;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileOptions;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.plugin.assembler.Assembler;
import ghidra.app.plugin.assembler.Assemblers;
import ghidra.app.script.GhidraScript;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.pcode.*;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.listing.Program;
import ghidra.program.model.pcode.PcodeBlockBasic;
import ghidra.program.model.symbol.*;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////// OllvmSolver /////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

public class OllvmSolver extends GhidraScript {
    public static final boolean DEBUG = true;

    @Override
    protected void run() throws Exception {
        JFrame frame = new JFrame();
        JFileChooser chooser = getjFileChooser();

        int flag = chooser.showOpenDialog(frame);

        // choose a json file
        if(flag == JFileChooser.APPROVE_OPTION) {
            // read all content from json file and deserialize it into SolverConfig
            SolverConfig config = new SolverConfig(chooser.getSelectedFile().getPath());
            // build decompiler for our scripts to get decompiled p-code, which is independent to assembly addresses
            DecompInterface decompiler = buildDecompiler(currentProgram);
            // main process for deobfuscation
            config.solve(this, currentProgram, decompiler);
        }
    }

    private static JFileChooser getjFileChooser() {
        JFileChooser chooser = new JFileChooser();
        // only accept json file
        // chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.getName().endsWith(".json");
            }

            @Override
            public String getDescription() {
                return "JSON File(*.json)";
            }
        });
        return chooser;
    }

    private DecompInterface buildDecompiler(Program program) throws Exception {
        DecompInterface decompInterface = new DecompInterface();
        DecompileOptions options = new DecompileOptions();
        PluginTool tool = state.getTool();
        if (tool != null) {
            OptionsService service = tool.getService(OptionsService.class);
            if (service != null) {
                ToolOptions opt = service.getOptions("Decompiler");
                options.grabFromToolAndProgram(null, opt, program);
            }
        }
        decompInterface.setOptions(options);
        decompInterface.toggleCCode(true);
        decompInterface.toggleSyntaxTree(true);
        decompInterface.setSimplificationStyle("decompile");
        if (!decompInterface.openProgram(currentProgram)) {
            throw new Exception(String.format("ERROR: Failed to open current program.\nError message: %s",
                    decompInterface.getLastMessage()));
        }
        return decompInterface;
    }

    public HighFunction decompileFunction(DecompInterface decompiler, Function func) throws Exception {
        HighFunction hFunction;

        try {
            DecompileResults dRes = decompiler.decompileFunction(func,
                    decompiler.getOptions().getDefaultTimeout(), this.getMonitor());
            hFunction = dRes.getHighFunction();
        }
        catch (Exception e) {
            throw new Exception(String.format(
                    "ERROR: Failed to decompile function specified.\nError message: %s", e.getMessage()));
        }

        return hFunction;
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////// SolverConfig /////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class SolverConfig {
        // Addresses of initializations of state variables of different functions
        Vector<LocalStateVarDeflatter> target_local_vars;

        // only accept "auto", "manual", "disabled". If auto, we will detect all global vars that no one write something in
        // them and check all functions. If manual, you need to set the global vars, and we will check all functions. If
        // disabled, we will leave global vars aside.
        String global_var_deobfuscation_mode;
        // Global variables only read with/without initializations
        Vector<Symbol> user_inputs_gvo;
        // you need to specify functions that is obfuscated by global vars
        Vector<Symbol> functions_for_gvo;

        public SolverConfig() {
            this.target_local_vars = new Vector<>();
            this.global_var_deobfuscation_mode = "auto";
            this.user_inputs_gvo = new Vector<>();
            this.functions_for_gvo = new Vector<>();
        }

        public SolverConfig(String json_path) throws Exception {
            GsonBuilder gson_builder = new GsonBuilder();
            gson_builder
                    .registerTypeAdapter(SolverConfig.class, new SolverConfigDeserializer())
                    .registerTypeAdapter(LocalStateVarDeflatter.class, new LocalStateVarDeflatterDeserializer());
            Gson gson = gson_builder.create();
            FileReader jsonReader = new FileReader(json_path);
            SolverConfig ret = gson.fromJson(jsonReader, SolverConfig.class);
            this.target_local_vars = ret.target_local_vars;
            this.global_var_deobfuscation_mode = ret.global_var_deobfuscation_mode;
            this.user_inputs_gvo = ret.user_inputs_gvo;
            this.functions_for_gvo = ret.functions_for_gvo;
        }

        public void solve(OllvmSolver main, Program program, DecompInterface decompiler) throws Exception {
            // handle every local variable obfuscation (Control Flow Flattening)
            println("[***] Start local state variable deobfuscation (Control Flow Flattening)\n");
            for (LocalStateVarDeflatter deflatter: target_local_vars) {
                printf("Solving variable initialized in %#x\n", deflatter.getVar_init_address_ctor());
                Function target_func = main.getFunctionContaining(main.toAddr(deflatter.getVar_init_address_ctor()));
                printf("Start handling function %s, wait a second...\n", target_func.getName());
                deflatter.postInitialization(main, program, decompiler);
                try {
                    deflatter.Deflat();
                } catch (Exception e) {
                    printf("Exception occurred while deflatting %#x\n",
                            deflatter.getVar_init_address_ctor());
                    e.printStackTrace();
                }
            }
            // handle read-only global vars
            println("[***] Start global variable deobfuscation (Control Flow Duplication)\n");
            GlobalVarHandler global_var_handler = null;
            if (global_var_deobfuscation_mode.equals("auto"))
                global_var_handler = new GlobalVarHandler();
            else if (global_var_deobfuscation_mode.equals("manual"))
                global_var_handler = new GlobalVarHandler(user_inputs_gvo);
            if (global_var_handler != null) {
                for (Symbol sym: this.functions_for_gvo) {
                    Function func = currentProgram.getListing().getFunctionAt(sym.getAddress());

                    global_var_handler.deobfuscate(decompileFunction(decompiler, func));
                    return;
                }
            }
        }
    }

    class SolverConfigDeserializer implements JsonDeserializer<SolverConfig> {
        @Override
        public SolverConfig deserialize(JsonElement jsonElement, Type type,
                                        JsonDeserializationContext jsonDeserializationContext)
                throws JsonParseException {
            SolverConfig ret = new SolverConfig();
            JsonObject object = jsonElement.getAsJsonObject();

            JsonArray local_var_arr = object.get("target_local_vars").getAsJsonArray();
            if (local_var_arr == null)
                throw new JsonParseException("Wrong json format, target_local_vars must be an array.");
            for (JsonElement element : local_var_arr) {
                JsonObject arr_element = element.getAsJsonObject();
                ret.target_local_vars.add(jsonDeserializationContext.deserialize(
                        arr_element, LocalStateVarDeflatter.class));
            }

            ret.global_var_deobfuscation_mode = object.get("global_var_deobfuscation_mode").getAsString();
            if (ret.global_var_deobfuscation_mode == null)
                throw new JsonParseException("Wrong json format, global_var_deobfuscation_mode must be a string.");
            if (!ret.global_var_deobfuscation_mode.equals("auto") &&
                    !ret.global_var_deobfuscation_mode.equals("manual") &&
                    !ret.global_var_deobfuscation_mode.equals("disabled"))
                throw new JsonParseException(
                        "Wrong global var deobfuscation mode, only 'auto', 'manual', 'disabled' is accepted");

            if (ret.global_var_deobfuscation_mode.equals("manual")) {
                JsonArray user_inputs = object.get("user_inputs_gvo").getAsJsonArray();
                if (user_inputs == null)
                    throw new JsonParseException("Wrong json format, user_inputs_gvo must be an array.");
                for (JsonElement element: user_inputs) {
                    JsonObject arr_element = element.getAsJsonObject();
                    Symbol sym = objectToSymbol(arr_element);
                    if (!sym.getSymbolType().toString().equals("Label"))
                        throw new JsonParseException(String.format(
                                "Invalid symbol %s(%#x), all symbols in user_inputs_gvo must be labels.",
                                sym.getName(), sym.getAddress().getOffset()));
                    ret.user_inputs_gvo.add(sym);
                }
            }

            JsonArray function_for_gvo = object.get("functions_for_gvo").getAsJsonArray();
            if (function_for_gvo == null)
                throw new JsonParseException("Wrong json format, functions_for_gvo must be an array.");
            for (JsonElement element: function_for_gvo) {
                Symbol sym = objectToSymbol(element);
                if (!sym.getSymbolType().toString().equals("Function"))
                    throw new JsonParseException(String.format(
                            "Invalid symbol %s(%#x), all symbols in user_inputs_gvo must be labels.",
                            sym.getName(), sym.getAddress().getOffset()));
                ret.functions_for_gvo.add(sym);
            }

            return ret;
        }

        public Symbol objectToSymbol(JsonElement element) throws JsonParseException {
            if (!element.isJsonPrimitive())
                throw new JsonParseException(
                        "Wrong format for symbol, should be a number for address or a string for symbol name");
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                SymbolIterator si = currentProgram.getSymbolTable().getSymbols(primitive.getAsString());
                Symbol ret = null;
                if (si.hasNext())
                    return si.next();
                else
                    throw new JsonParseException(String.format("No symbol named %s found", primitive.getAsString()));
            } else if (primitive.isNumber()) {
                Symbol[] symbols = currentProgram.getSymbolTable().getSymbols(toAddr(primitive.getAsLong()));
                println("length: " + String.valueOf(symbols.length));
                if (symbols.length != 0)
                    return symbols[0];
                else
                    throw new JsonParseException(String.format("No symbol at %#x found", primitive.getAsLong()));
            } else
                throw new JsonParseException(String.format(
                        "Wrong format for symbol, should be a number for address or a string for symbol name," +
                                " %s found", primitive));
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////// LocalStateVarDeflatter /////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * LocalStateVarDeflatter: Control flow deflatter towards local variable obfuscation.
 */
    class LocalStateVarDeflatter {
        // variable size, only 4 or 8 supported now
        private final long var_size;
        // only used in constructors. The json file cannot include SolverMain, so we need second initialization
        // (postInitialization)
        private final long var_init_address_ctor;
        // memory address which contains the instruction that initializes local variable
        private Address var_init_address;
        private OllvmSolver solverMain;
        private Program program;

        private Memory mem;
        private DecompInterface decompiler;

        public long getVar_size() {
            return this.var_size;
        }

        public long getVar_init_address_ctor() {
            return this.var_init_address_ctor;
        }

        public LocalStateVarDeflatter(long var_size, long var_init_address) {
            this.var_size = var_size;
            this.var_init_address_ctor = var_init_address;
        }

        // MUST BE CALLED AFTER NEW! we cannot analyse without a program and a pre-configured decompiler!
        public void postInitialization(OllvmSolver main, Program program, DecompInterface decompiler) {
            this.solverMain = main;
            this.program = program;
            this.decompiler = decompiler;
            this.var_init_address = main.toAddr(this.var_init_address_ctor);
            this.mem = program.getMemory();
        }

        public void Deflat() throws Exception {
            Function func = solverMain.getFunctionContaining(this.var_init_address);
            // variable initialization address doesn't belong to any known function, cannot work anymore
            if (func == null) {
                throw new Exception(String.format("ERROR: No function contains init address given: %#x",
                        this.var_init_address.getOffset()));
            }

            // decompile target function, this step won't fail normally
            HighFunction hFunction = solverMain.decompileFunction(decompiler, func);

            // get the VarNode of the state variable through specified instruction address
            Varnode dispatcher = this.getStateVarNode(hFunction);

            // find the relationships between different state variable values and target blocks
            this.findRealBlockRelations(hFunction, dispatcher);

            ConditionBlock[] relations = this.findRealBlockRelations(hFunction, dispatcher);

            if (DEBUG)
                for (ConditionBlock cb: relations) {
                    println(cb.toString());
                }

            DefBlock dft = this.buildDispatcherDFT(hFunction, dispatcher.getDef());

            ControlFlowMap[] cfg = this.recoverExecFlow(relations, dft);

            if (DEBUG)
                for(ControlFlowMap c: cfg) {
                    println(c.toString());
                }

            this.recoverCFG(cfg);
        }

        Varnode getStateVarNode(HighFunction hFunction) throws Exception {
            // get all p-codes of this address
            Iterator<PcodeOpAST> ops = hFunction.getPcodeOps(this.var_init_address);
            PcodeOp copy_pcode = null;
            while (ops.hasNext()) {
                PcodeOpAST op = ops.next();
                // we regard COPY a Constant as the initialization of the state variable
                if (op.getOpcode() == PcodeOp.COPY && op.getInput(0).isConstant()) {
                    printf("Found COPY from const to varnode: %s\n", op);
                    copy_pcode = op;
                }
            }

            // We need to trace the output of the p-code we found above, until we meet a MULTIEQUAL p-code,
            // the output of MULTIEQUAL p-code is the VarNode of our state variable we want to find.
            while(copy_pcode != null && copy_pcode.getOpcode() != PcodeOp.MULTIEQUAL) {
                if(copy_pcode.getOutput() == null) {
                    throw new Exception(String.format("No output found in %s\n", copy_pcode));
                }
                copy_pcode = copy_pcode.getOutput().getLoneDescend();
                if(copy_pcode == null) {
                    throw new Exception("ERROR: Failed to find lone descendant for P-code");
                }
                if (DEBUG)
                    printf("Lone descendant found: %s\n", copy_pcode);
            }

            assert copy_pcode != null;

            return copy_pcode.getOutput();
        }

        // Get all blocks that use state variable as conditions
        ConditionBlock[] findRealBlockRelations(HighFunction hFunction, Varnode target) {
            Vector<ConditionBlock> ret = new Vector<>();
            for (PcodeBlockBasic block: hFunction.getBasicBlocks()){
                // Only CBRANCH has 2 output, we discard blocks without CBRANCH as its end
                if(block.getOutSize() != 2)
                    continue;

                PcodeOp block_end = block.getLastOp();
                if(block_end.getOpcode() != PcodeOp.CBRANCH)
                    continue;

                // CBRANCH has 2 inputs, [0] is jump target, [1] is condition
                Varnode condition = block_end.getInput(1);

                // get the p-code which defines the value of condition
                PcodeOp condition_def = condition.getDef();
                if(!(condition_def.getOpcode() == PcodeOp.INT_EQUAL ||
                        condition_def.getOpcode() == PcodeOp.INT_NOTEQUAL))
                    continue;

                for (Iterator<PcodeOp> it = block.getIterator(); it.hasNext(); ) {
                    PcodeOp op = it.next();

                    if (DEBUG)
                        println(op.toString());
                }

                if(condition_def.getInput(0).isConstant() &&
                        condition_def.getInput(1).equals(target))
                    ret.add(new ConditionBlock(
                            condition_def.getInput(0).getOffset(),
                            condition_def.getOpcode() == PcodeOp.INT_EQUAL ?
                                    (PcodeBlockBasic) block.getTrueOut() : (PcodeBlockBasic) block.getFalseOut(),
                            condition_def.getOpcode()
                    ));
                else if(condition_def.getInput(1).isConstant() &&
                        condition_def.getInput(0).equals(target))
                    ret.add(new ConditionBlock(
                            condition_def.getInput(1).getOffset(),
                            condition_def.getOpcode() == PcodeOp.INT_EQUAL ?
                                    (PcodeBlockBasic) block.getTrueOut() : (PcodeBlockBasic) block.getFalseOut(),
                            condition_def.getOpcode()
                    ));
            }
            return ret.toArray(new ConditionBlock[0]);
        }


        DefBlock buildDispatcherDFT(HighFunction hFunction, PcodeOp multiEqual, int recursive_depth,
                                    DefBlock child) throws Exception {
            DefBlock root = new DefBlock(0, multiEqual.getParent(), child);

            Vector<DefBlock> ret = new Vector<>();
            // All the inputs except itself regarded as data flow sources (parents)
            Varnode[] inputs = multiEqual.getInputs();
            // All inputs
            for(Varnode input: inputs) {
                if(input == multiEqual.getOutput())
                    continue;
                PcodeOp source_op = input.getDef();
                // input Opcode == COPY ?
                if(source_op.getOpcode() == PcodeOp.COPY){
                    // input VarNode == Constant ?
                    if(source_op.getInput(0).isConstant()){
                        ret.add(new DefBlock(source_op.getInput(0).getOffset(), source_op.getParent(), root));
                    } else if (source_op.getInput(0).getAddress().getAddressSpace().getName().equals("ram")) {
                        if (source_op.getInput(0).isAddress()) {
                            if (this.var_size == 4) {
                                int ram_val = mem.getInt(source_op.getInput(0).getAddress());
                                ret.add(new DefBlock(ram_val, source_op.getParent(), root));
                            } else if (this.var_size == 8) {
                                long ram_val = mem.getLong(source_op.getInput(0).getAddress());
                                ret.add(new DefBlock(ram_val, source_op.getParent(), root));
                            } else {
                                throw new Exception("Invalid dispatcher size");
                            }
                        } else {
                            // may be registers
                            throw new Exception("Non-const value for dispatcher found, cannot recover");
                        }
                    } else {
                        // build DFT recursively
                        ret.add(this.buildDispatcherDFT(hFunction, source_op, recursive_depth + 1, root));
                    }
                } else if (source_op.getOpcode() == PcodeOp.MULTIEQUAL) {
                    // analyse MULTIEQUAL recursively
                    for (Varnode ignored : source_op.getInputs()) {
                        ret.add(this.buildDispatcherDFT(hFunction, source_op, recursive_depth + 1, root));
                    }
                } else {
                    throw new Exception("Unsupported pcode for tracing data flow tree");
                }
            }

            root.parents = ret;
            if(root.parents.size() == 1)
                root.constant = root.parents.get(0).constant;
            return root;
        }

        // Get all blocks that change the value of state variable
        DefBlock buildDispatcherDFT(HighFunction hFunction, PcodeOp multiEqual) throws Exception {
            return this.buildDispatcherDFT(hFunction, multiEqual, 1, null);
        }

        ControlFlowMap[] recoverExecFlow(ConditionBlock[] relations, DefBlock dft) throws Exception {
            Vector<ControlFlowMap> ret = new Vector<>();

            // DefBlock[] ancestors = dft.getAncestors();    // src, dst
            DefBlock[] ancestors = dft.parents.toArray(new DefBlock[0]);
            for(DefBlock defBlock : ancestors) {
                PcodeBlockBasic block = defBlock.block;
                // JMP without conditions
                // Just mark the condition block after definition block
                if (block.getOutSize() == 1) {
                    PcodeBlockBasic conditionBlock = findConditionBlock(relations, defBlock.constant);
                    if(conditionBlock == null)
                        throw new Exception(
                                String.format("Failed to find any block using constant %#x", defBlock.constant));
                    // We need to skip all lone descend for CMOVxx
                    if(block.getStart().getOffset() == block.getStop().getOffset() &&
                            this.program.getListing()
                                    .getInstructions(block.getStart(), true).next().getMnemonicString()
                                    .startsWith("CMOV"))
                        continue;
                    ret.add(new ControlFlowMap(block, conditionBlock));

                } else if (block.getOutSize() == 2) {
                    PcodeBlockBasic trueLinker, falseLinker;
                    long trueConst, falseConst;
                    // JMP with conditions
                    // There are 2 descend blocks, true or false
                    PcodeBlockBasic trueDesc = (PcodeBlockBasic) block.getTrueOut(),
                            falseDesc = (PcodeBlockBasic) block.getFalseOut();
                    DefBlock tdd = findDefBlock(dft, trueDesc);
                    // If there is a definition of state var in true descendant
                    // Treat trueDesc's definition as true branch
                    trueConst = Objects.requireNonNullElse(tdd, defBlock).constant;
                    trueLinker = findConditionBlock(relations, trueConst);
                    if (trueLinker == null) {
                        throw new Exception(String.format(
                                "Failed to find descendant for condition TRUE:\n" +
                                        "Block: %#x - %#x",
                                block.getStart().getOffset(), block.getStop().getOffset())
                        );
                    }

                    DefBlock fdd = findDefBlock(dft, falseDesc);
                    falseConst = Objects.requireNonNullElse(fdd, defBlock).constant;
                    falseLinker = findConditionBlock(relations, falseConst);
                    // If there is a definition of state var in true descendant
                    // Treat trueDesc's definition as true branch
                    if(falseLinker == null) {
                        throw new Exception(String.format(
                                "Failed to find descendant for condition FALSE:\n" +
                                        "Block: %#x - %#x",
                                block.getStart().getOffset(), block.getStop().getOffset())
                        );
                    }

                    // 2 branch must have different state variable values
                    if (trueConst == falseConst && falseConst == defBlock.constant) {
                        throw new Exception(String.format(
                                "Condition block cannot have 2 descendant without changing state var.\n" +
                                        "Block: %#x - %#x, True: %#x - %#x, False: %#x - %#x",
                                block.getStart().getOffset(), block.getStop().getOffset(),
                                trueLinker.getStart().getOffset(), trueLinker.getStop().getOffset(),
                                falseLinker.getStart().getOffset(), falseLinker.getStop().getOffset()
                        ));
                    }

                    ret.add(new ControlFlowMap(block, trueLinker, falseLinker));

                } else {
                    throw new Exception(String.format(
                            "Unsupported block for %d outputs", block.getOutSize()
                    ));
                }
            }
            return ret.toArray(new ControlFlowMap[0]);
        }

        PcodeBlockBasic findConditionBlock(ConditionBlock[] relations, long constant) {
            for(ConditionBlock relation: relations) {
                if(relation.constant == constant)
                    return relation.target;
            }
            return null;
        }

        DefBlock findDefBlock(DefBlock root, PcodeBlockBasic block) {
            if(root.block.equals(block) && root.child != null)
                return root;
            else if(root.block.equals(block) && root.child == null) { // wouldn't return real root
                return null;
            }
            if(root.parents == null) {
                return null;
            }
            for(DefBlock d: root.parents) {
                DefBlock find = findDefBlock(d, block);
                if(find != null)
                    return find;
            }
            return null;
        }

        void recoverCFG(ControlFlowMap[] cfg) throws Exception {
            String arch = this.program.getLanguage().getProcessor().toString();
            if(arch.equals("x86"))
                this.recoverCFGx86(cfg);
        }

        void recoverCFGx86(ControlFlowMap[] cfg) throws Exception {
            CFGPatcherX86 patcher = new CFGPatcherX86(this.program, cfg);
            patcher.patchAll();
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////// ConditionBlock /////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class ConditionBlock {
        public long constant;
        public PcodeBlockBasic target;
        public int compare_condition;
        public boolean linked;

        public ConditionBlock(long constant, PcodeBlockBasic target, int condition) {
            this.constant = constant;
            this.target = target;
            this.compare_condition = condition;
            linked = false;
        }

        @Override
        public String toString() {
            return String.format(
                    "when state var = %#x, jump to %#x - %#x", this.constant, this.target.getStart().getOffset(),
                    this.target.getStop().getOffset()
            );
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////// DefBlock ///////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class DefBlock {
        public PcodeBlockBasic block;
        public long constant;
        public DefBlock child;
        public Vector<DefBlock> parents;

        public DefBlock(long constant, PcodeBlockBasic block) {
            this.constant = constant;
            this.block = block;
            this.child = null;
            this.parents = null;
        }

        public DefBlock(long constant, PcodeBlockBasic block, DefBlock child) {
            this.constant = constant;
            this.block = block;
            this.child = child;
            this.parents = null;
        }

        public DefBlock[] getAncestors() throws Exception {
            Vector<DefBlock> ret = new Vector<>();
            for(DefBlock d: this.parents) {
                if(d.parents != null) {
                    if(!ret.addAll(List.of(d.getAncestors())))
                        throw new Exception("Failed to merge ancestors");
                } else {
                    ret.add(d);
                }
            }
            return ret.toArray(new DefBlock[0]);
        }

        @Override
        public String toString() {
            return this.toString(0);
        }

        private String toString(int tab) {
            StringBuilder builder = new StringBuilder("\t".repeat(Math.max(0, tab)) + this.selfToString());
            if (this.parents != null) {
                for(DefBlock t: parents) {
                    builder.append(t.toString(tab + 1));
                }
            }
            return builder.toString();
        }

        public String selfToString() {
            return String.format("Block %#x to %#x, value %#x\n",
                    this.block.getStart().getOffset(), this.block.getStop().getOffset(), this.constant);
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////// ControlFlowMap ////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class ControlFlowMap {
        public PcodeBlockBasic target;
        public PcodeBlockBasic trueDescend;
        public PcodeBlockBasic falseDescend;

        public ControlFlowMap(PcodeBlockBasic target, PcodeBlockBasic loneDescend) {
            this.target = target;
            this.trueDescend = loneDescend;
            this.falseDescend = null;
        }

        public ControlFlowMap(PcodeBlockBasic target, PcodeBlockBasic trueDescend, PcodeBlockBasic falseDescend) {
            this.target = target;
            this.trueDescend = trueDescend;
            this.falseDescend = falseDescend;
        }

        @Override
        public String toString() {
            if(falseDescend == null) {
                return String.format(
                        "block: %#x to %#x\n\tLone descend: %#x to %#x", target.getStart().getOffset(),
                        target.getStop().getOffset(), trueDescend.getStart().getOffset(),
                        trueDescend.getStop().getOffset()
                );
            } else {
                return String.format(
                        "block: %#x to %#x\n\tTrue descend: %#x to %#x\n\tFalse descend: %#x to %#x",
                        target.getStart().getOffset(), target.getStop().getOffset(),
                        trueDescend.getStart().getOffset(), trueDescend.getStop().getOffset(),
                        falseDescend.getStart().getOffset(), falseDescend.getStop().getOffset()
                );
            }
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////// LocalStateVarDeflatterDeserializer //////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class LocalStateVarDeflatterDeserializer implements JsonDeserializer<LocalStateVarDeflatter> {

        @Override
        public LocalStateVarDeflatter deserialize(JsonElement jsonElement, Type type,
                                                  JsonDeserializationContext jsonDeserializationContext)
                throws JsonParseException {
            JsonObject object = jsonElement.getAsJsonObject();

            long var_size;
            if (object.get("var_size").getAsJsonPrimitive().isNumber()) {
                var_size = object.get("var_size").getAsLong();
            } else {
                throw new JsonParseException("Error: Failed to parse json file, var_size must be a number");
            }

            if (var_size != 4 && var_size != 8) {
                throw new JsonParseException("Error: Failed to parse json file, var_size must be 4 or 8");
            }

            long var_init_address;
            if (object.get("var_init_address").getAsJsonPrimitive().isString()) {
                try {
                    var_init_address = Long.parseLong(object.get("var_init_address").getAsString(), 16);
                } catch (NumberFormatException e) {
                    throw new JsonParseException(
                            "Error: Failed to parse json file, parsing var_init_address hex string failed");
                }
            } else if (object.get("var_init_address").getAsJsonPrimitive().isNumber()) {
                var_init_address = object.get("var_init_address").getAsLong();
            } else {
                throw new JsonParseException(
                        "Error: Failed to parse json file, var_init_address must be hex string or number");
            }

            return new LocalStateVarDeflatter(
                    var_size,
                    var_init_address
            );
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////// CFGPatcher //////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class ASMPatcher {
        Program program;
        Assembler asm;

        ASMPatcher(Program program) {
            this.program = program;
            this.asm = Assemblers.getAssembler(this.program);
        }

        public PatchEntry patch(Address addr, String mnemonic) throws Exception {
            InstructionIterator ii = this.asm.assemble(addr, mnemonic);
            Vector<Byte> new_machine_code = new Vector<>();
            for(Instruction i: ii) {
                for(byte b: i.getBytes())
                    new_machine_code.add(b);
            }
            byte[] out = new byte[new_machine_code.size()];
            for(int i=0; i<new_machine_code.size(); i++)
                out[i] = new_machine_code.get(i);

            PatchEntry ret = new PatchEntry(addr, out);
            if (DEBUG)
                println(ret.toString());

            fillInvalidWithNop(addr.add(new_machine_code.size()));
            return ret;
        }

        public void fillInvalidWithNop(Address addr) throws Exception {
            int fill_len = 0;
            while (true) {
                Instruction inst = program.getListing().getInstructionAt(addr);
                if (inst == null)
                    fillNop(addr, 1);
                else {
                    println(inst.toString());
                    break;
                }
                addr = addr.add(1);
                fill_len++;
            }
            if (DEBUG)
                printf("Filled %#x with nop, length %d\n", addr.getOffset(), fill_len);
        }

        public void fillNop(Address addr, int size) throws Exception {
            this.asm.assemble(addr, "NOP\n".repeat(size));
        }
    }

    abstract class CFGPatcher extends ASMPatcher {
        ControlFlowMap[] cfg;
        Listing listing;

        CFGPatcher(Program program, ControlFlowMap[] cfg) {
            super(program);
            this.cfg = cfg;

            this.listing = this.program.getListing();
        }

        abstract String GenUncondBlockEnd(long target_addr);
        abstract String GenCondBlockEnd(Instruction ins, long true_addr, long false_addr);
        public void patchAll() throws Exception {
            for(ControlFlowMap cfg_entry: cfg) {
                PatchEntry pe = this.patchOne(cfg_entry);
            }
        }

        PatchEntry patchOne(ControlFlowMap map) throws Exception {
            PcodeBlockBasic root = map.target;
            Instruction to_patch = this.listing.getInstructions(root.getStop(), true).next();
            String asm_str;
            Address patch_address = to_patch.getMinAddress();
            // Unconditional JUMP
            if (map.falseDescend == null) {
                long target = map.trueDescend.getStart().getOffset();
                asm_str = GenUncondBlockEnd(target);
                byte[] new_machine_code = this.asm.assembleLine(patch_address, asm_str);
                if(new_machine_code.length > to_patch.getLength()){
                    throw new Exception(String.format(
                            "Not enough space to patch \"%s\" in %#x", asm_str, patch_address.getOffset()
                    ));
                }
            } else {
                // Conditional JUMP
                long true_desc = map.trueDescend.getStart().getOffset();
                long false_desc = map.falseDescend.getStart().getOffset();
                asm_str = GenCondBlockEnd(to_patch, true_desc, false_desc);
            }

            if(asm_str == null) {
                throw new Exception("Failed to patch conditional jump.");
            }
            return patch(patch_address, asm_str);
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////// CFGPatcherX86 /////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class CFGPatcherX86 extends CFGPatcher {
        public CFGPatcherX86(Program program, ControlFlowMap[] cfg) {
            super(program, cfg);
        }

        @Override
        public String GenUncondBlockEnd(long target_addr) {
            return String.format("JMP %#x", target_addr);
        }

        @Override
        public String GenCondBlockEnd(Instruction ins, long true_addr, long false_addr) {
            String ins_mne = ins.getMnemonicString();
            if (ins_mne.startsWith("CMOV")){
                return String.format("%s %#x\nJMP %#x\n", ins_mne.replace("CMOV", "J"),
                        true_addr, false_addr);
            }
            return null;
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////// PatchEntry //////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class PatchEntry {
        Address address;
        byte[] to_patch;

        public PatchEntry(Address address, byte[] to_patch){
            this.address = address;
            this.to_patch = to_patch;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("address: %#x\nbytes: ", address.getOffset()));
            for(byte b: this.to_patch) {
                builder.append(String.format("%02x ", b));
            }
            return builder.toString();
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////// GlobalVarHandler ///////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    class GlobalVarHandler {
        Vector<Symbol> read_only_global_vars;

        public GlobalVarHandler() {
            this.read_only_global_vars = new Vector<>();
            get_all_global_vars();

            Vector<Symbol> all_data = get_all_global_vars();
            for (Symbol symbol: all_data) {
                if (GlobalVarHandler.symbolIsReadonly(symbol))
                    read_only_global_vars.add(symbol);
            }
        }

        public GlobalVarHandler(Vector<Symbol> addrs) {
            this.read_only_global_vars = addrs;
        }

        public Vector<Symbol> get_all_global_vars() {
            Vector<Symbol> ret = new Vector<>();
            for (Symbol symbol: currentProgram.getSymbolTable().getSymbolIterator()) {
                // remove 0-ref labels
                if (symbol.getReferenceCount() == 0)
                    continue;
                // remove non-label symbols
                if (!symbol.getSymbolType().toString().equals("Label"))
                    continue;
                Data data = currentProgram.getListing().getDataAt(symbol.getAddress());
                if (data == null) {
                    continue;
                }
                if (data.isPointer() || data.isArray() || data.isStructure()) {
                    continue;
                }
                ret.add(symbol);
            }
            return ret;
        }

        public static boolean symbolIsReadonly(Symbol sym) {
            Reference[] refs = sym.getReferences();
            boolean is_readonly = true;
            for (Reference ref: refs) {
                if (!(ref.isExternalReference() || ref.getReferenceType().isRead())) {
                    is_readonly = false;
                    break;
                }
            }
            return is_readonly;
        }

        public void deobfuscate(HighFunction hFunction) throws Exception {
            Function function = hFunction.getFunction();

            Listing listing = currentProgram.getListing();

            for (Address addr: function.getBody().getAddresses(true)) {
                Instruction inst = listing.getInstructionAt(addr);
                if (inst == null)
                    continue;
                for (Iterator<PcodeOpAST> it = hFunction.getPcodeOps(addr); it.hasNext(); ) {
                    PcodeOpAST op = it.next();

                    if (op.getOpcode() != PcodeOp.CBRANCH)
                        continue;
                    Varnode condition = op.getInput(1);
                    ArithmeticNode def_tree_root = new ArithmeticNode(condition);
                    if (!def_tree_root.buildBranches())
                        continue;

                    if (!def_tree_root.isAllArgumentsConstantOrMemory())
                        continue;

                    long exp_value = def_tree_root.doCalculation();

                    if (DEBUG) {
                        printf("Calculation result: %#x\n", exp_value);
                    }
                    printf("Patching: %#x\n", addr.getOffset());

                    doPatch(addr, exp_value != 0);
                }
            }
        }

        public void doPatch(Address addr, boolean condition) throws Exception {
            Instruction inst = currentProgram.getListing().getInstructionAt(addr);
            if (inst == null)
                return;
            int inst_len = inst.getLength();
            String mnemonic = inst.toString();
            if (!mnemonic.matches("J[A-Z]+ 0x[\\da-f]+"))
                return;
            String[] s = mnemonic.split(" ");
            assert(s.length == 2);
            String j_inst = s[0];
            String j_target = s[1];
            String patched = "";
            if (condition ^ j_inst.contains("N"))
                patched = "JMP " + j_target;
            else
                patched = "NOP";
            ASMPatcher patcher = new ASMPatcher(currentProgram);
            patcher.patch(addr, patched);
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////// ArithmeticTree ////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * ArithmeticNode: Used to define an arithmetic calculation process.
     * It's designed as an upside-down tree, the root node is at the bottom, representing final answer.
     * Every node is a VarNode or a value. If all VarNode has a certain value, we can get our answer.
     */
    class ArithmeticNode {
        public static final int INTERMEDIATE_VARNODE = 0;
        public static final int SINGLE_VARNODE = 1;
        public static final int CONSTANT = 2;
        private int node_type;
        private Varnode node;
        private int opcode;
        private ArithmeticNode input1;
        private ArithmeticNode input2;
        private Long value;             // the answer

        public ArithmeticNode(Varnode node) {
            this.node = node;
            this.node_type = INTERMEDIATE_VARNODE;
            if (this.node.isConstant())
                this.node_type = CONSTANT;
        }

        // get all definition process of this varnode, you will get a tree
        public boolean buildBranches() {
            if (this.node == null)
                return false;
            else if (this.node_type == CONSTANT || this.node_type == SINGLE_VARNODE)
                return true;
            PcodeOp source_op = this.node.getDef();
            assert(source_op.getOutput().equals(this.node));
            opcode = source_op.getOpcode();

            if (opcode == PcodeOp.MULTIEQUAL || opcode == PcodeOp.INDIRECT) {
                node_type = SINGLE_VARNODE;
                return true;
            } else if (opcode == PcodeOp.CALL)
                return false;

            input1 = source_op.getInputs().length >= 1 ? new ArithmeticNode(source_op.getInput(0)) : null;
            input2 = source_op.getInputs().length >= 2 ? new ArithmeticNode(source_op.getInput(1)) : null;

            // If this varnode is defined by COPY, then the input of COPY is one of the final source, end recursion
            if (opcode == PcodeOp.COPY) {
                assert(input1 != null && input2 == null);
                if (input1.node.isConstant())
                    input1.node_type = CONSTANT;
                else if (input1.node.isAddress())
                    input1.node_type = SINGLE_VARNODE;
                else
                    return false;
                return true;
            }

            if (input1 != null)
                if (!input1.buildBranches())
                    return false;
            if (input2 != null)
                return input2.buildBranches();
            return true;
        }

        // get all arguments that determines the value of this varnode.
        //
        // For example: this.node = (a * (a+1) - 2) / (b - c), then you will get [a, b, c, 1, -2]
        public Vector<Varnode> getAllArithmeticArguments() {
            Vector<Varnode> ret = new Vector<>();
            if (this.input1 != null)
                input1.getAllArithmeticArguments(ret);
            if (this.input2 != null)
                input2.getAllArithmeticArguments(ret);
            return ret;
        }

        private void getAllArithmeticArguments(Vector<Varnode> nodes) {
            if (this.node_type != INTERMEDIATE_VARNODE) {
                nodes.add(this.node);
                return;
            }

            if (this.input1 != null)
                input1.getAllArithmeticArguments(nodes);
            if (this.input2 != null)
                input2.getAllArithmeticArguments(nodes);
        }

        public boolean isAllArgumentsConstantOrMemory() {
            Vector<Varnode> nodes = getAllArithmeticArguments();
            for (Varnode node: nodes) {
                if (!node.isConstant() && !node.getAddress().getAddressSpace().isMemorySpace())
                    return false;
            }
            return true;
        }

        // in doCalculation, every varnode will be regarded as 0
        public long doCalculation() throws Exception {
            if (this.node_type == CONSTANT) {
                assert this.node.isConstant();
                return this.node.getOffset();
            } else if (this.node_type == SINGLE_VARNODE) {
                return 0;
            } else {
                switch (this.opcode) {
                    case PcodeOp.BOOL_AND -> {
                        return (this.input1.doCalculation() != 0) && (this.input2.doCalculation() != 0) ? 1 : 0;
                    }
                    case PcodeOp.BOOL_OR -> {
                        return (this.input1.doCalculation() != 0) || (this.input2.doCalculation() != 0) ? 1 : 0;
                    }
                    case PcodeOp.BOOL_XOR -> {
                        return (this.input1.doCalculation() != 0) ^ (this.input2.doCalculation() != 0) ? 1 : 0;
                    }
                    case PcodeOp.BOOL_NEGATE -> {
                        return this.input1.doCalculation() != 0 ? 0 : 1;
                    }
                    case PcodeOp.INT_ADD -> {
                        return this.input1.doCalculation() + this.input2.doCalculation();
                    }
                    case PcodeOp.INT_SUB -> {
                        return this.input1.doCalculation() - this.input2.doCalculation();
                    }
                    case PcodeOp.INT_MULT -> {
                        return this.input1.doCalculation() * this.input2.doCalculation();
                    }
                    case PcodeOp.INT_DIV -> {
                        return this.input1.doCalculation() / this.input2.doCalculation();
                    }
                    case PcodeOp.INT_REM -> {
                        return this.input1.doCalculation() % this.input2.doCalculation();
                    }
                    case PcodeOp.INT_2COMP -> {
                        return -this.input1.doCalculation();
                    }
                    case PcodeOp.INT_EQUAL -> {
                        return this.input1.doCalculation() == this.input2.doCalculation() ? 1 : 0;
                    }
                    case PcodeOp.INT_NOTEQUAL -> {
                        return this.input1.doCalculation() != this.input2.doCalculation() ? 1 : 0;
                    }
                    case PcodeOp.INT_LESS -> {
                        return Long.compareUnsigned(this.input1.doCalculation(), this.input2.doCalculation()) < 0
                                ? 1 : 0;
                    }
                    case PcodeOp.INT_SLESS -> {
                        return this.input1.doCalculation() < this.input2.doCalculation() ? 1 : 0;
                    }
                    case PcodeOp.INT_LESSEQUAL -> {
                        return Long.compareUnsigned(this.input1.doCalculation(), this.input2.doCalculation()) <= 0
                                ? 1 : 0;
                    }
                    case PcodeOp.INT_SLESSEQUAL -> {
                        return this.input1.doCalculation() <= this.input2.doCalculation() ? 1 : 0;
                    }
                    case PcodeOp.INT_AND -> {
                        return this.input1.doCalculation() & this.input2.doCalculation();
                    }
                    case PcodeOp.INT_OR -> {
                        return this.input1.doCalculation() | this.input2.doCalculation();
                    }
                    case PcodeOp.INT_XOR -> {
                        return this.input1.doCalculation() ^ this.input2.doCalculation();
                    }
                    case PcodeOp.INT_NEGATE -> {
                        return ~this.input1.doCalculation();
                    }
                    case PcodeOp.INT_LEFT -> {
                        return this.input1.doCalculation() << this.input2.doCalculation();
                    }
                    case PcodeOp.INT_RIGHT -> {
                        return this.input1.doCalculation() >> this.input2.doCalculation();
                    }
                    case PcodeOp.INT_SRIGHT -> {
                        return this.input1.doCalculation() >>> this.input2.doCalculation();
                    }
                    default -> throw new Exception(
                            String.format("Unsupported P-code opcode for calculation: %d", opcode));
                }
            }
        }
    }
}