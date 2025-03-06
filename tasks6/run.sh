clear
echo "running"
kotlin -cp build:lib/moshi.jar:lib/okio.jar:lib/moshi-kotlin.jar SsaKt -f simple.json
echo "done"
