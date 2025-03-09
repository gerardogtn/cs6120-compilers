clear
cat ../../tools/bril/benchmarks/core/hanoi.bril | bril2json |  kotlin -cp build:lib/moshi.jar:lib/okio.jar:lib/moshi-kotlin.jar SsaKt | bril2txt
