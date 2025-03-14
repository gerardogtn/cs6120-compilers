clang -c tracker.c
clang -fpass-plugin=build/pass/InstructionCountPass.dylib -c sort.c
cc tracker.o sort.o
