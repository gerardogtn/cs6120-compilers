# Variable and Args Names transformation

Are you tired of automatically generated variable names being dry and boring?
Have you had enough of v0, v1, v2,...?
Do you sometimes get annoyed that variables have a different character length (i.e. v0 vs v100)?

Fear not, this tool will make sure that all variables have unique mnemonic fixed-length variable names! Like `rust-amber-shield` or `plum-glass-camera`*.


* : up to 1,000 variables supported. 

## Setup
* We use Moshi/Okio for json parsing.

## Running
* Run `./setup.sh` to set up jars (requires wget)
* Run `./build.sh` to compile the source code
* Run `./run.sh <filename>` to run the transformation on a filename.
    * Filename must contain a Bril JSON file.
    * Output will be printed to standard output.

