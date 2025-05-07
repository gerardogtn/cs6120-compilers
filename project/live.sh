echo "starting"
cat fib.bril | bril2json | ./run.sh fib.trace | bril2txt
