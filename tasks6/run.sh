clear
echo "running"
kotlin -cp build:lib/moshi.jar:lib/okio.jar:lib/moshi-kotlin.jar SsaKt $1 $2
echo "done"
