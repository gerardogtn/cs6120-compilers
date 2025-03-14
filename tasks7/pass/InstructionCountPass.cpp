#include "llvm/Pass.h"
#include "llvm/IR/Module.h"
#include "llvm/IR/IRBuilder.h"
#include "llvm/Passes/PassBuilder.h"
#include "llvm/Passes/PassPlugin.h"
#include "llvm/Support/raw_ostream.h"

using namespace llvm;

namespace {
    
    struct InstructionCountPass : public PassInfoMixin<InstructionCountPass> {

        PreservedAnalyses run(
                Module &M, 
                ModuleAnalysisManager &AM
        ) {
            int i = 0;
            auto &ctxt = M.getContext();
            auto incrementCall = M.getOrInsertFunction(
                "increment",
                Type::getVoidTy(ctxt),
                Type::getInt32Ty(ctxt)
            );

            for (auto &F : M) {
                std::string name = F.getName().str();
                errs() << "id: " << i << " corresponds to: " << name << "\n";
                for (auto &BB : F) {
                    const auto &instrs = BB.instructionsWithoutDebug();
                    int len = std::distance(instrs.begin(), instrs.end());

                    IRBuilder<> builder(&* BB.getFirstInsertionPt());
                    builder.CreateCall(incrementCall, {
                        builder.getInt32(i),
                        builder.getInt32(len),
                    });
                }
                i++;
            }
            auto reportCall = M.getOrInsertFunction(
                "report",
                Type::getVoidTy(ctxt)
            );
            auto main = M.getFunction("main");
            auto &lastBb = main->back();
            auto terminator = lastBb.getTerminator();
            IRBuilder<> builder(terminator);
            builder.SetInsertPoint(terminator);
            builder.CreateCall(reportCall, { });
            return PreservedAnalyses::all();
        };
    };
}

extern "C" LLVM_ATTRIBUTE_WEAK ::llvm::PassPluginLibraryInfo
llvmGetPassPluginInfo() {
    return {
        .APIVersion = LLVM_PLUGIN_API_VERSION,
        .PluginName = "Instruction Count Pass",
        .PluginVersion = "v0.1",
        .RegisterPassBuilderCallbacks = [](PassBuilder &PB) {
            PB.registerPipelineStartEPCallback(
                [](ModulePassManager & MPM, OptimizationLevel level) {
                    MPM.addPass(InstructionCountPass());
                }
            );
        }
    };
}
