clear
echo "running"
kotlin -cp build:lib/moshi.jar:lib/okio.jar:lib/moshi-kotlin.jar DominanceKt $1 $2
echo "done"
