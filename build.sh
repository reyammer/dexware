#!/bin/bash

echo "Discalimer: consider this script as a starting point. I'm sure it will not work on your machine!"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR

OUTPUT=$DIR/data
mkdir -p $OUTPUT

MAINJAR=dexware.jar

cd DexWare/bin/
dx --dex --output=$MAINJAR *.class
cd - > /dev/null

# compile the jni bit
gcc-4.6 -o DexWare/jni/FileDescriptorCleaner.so -I/usr/lib/jvm/jdk1.6.0_34/include/ -I/usr/lib/jvm/jdk1.6.0_34/include/linux -I./DexWare/jni -m32 -shared -fPIC DexWare/jni/FileDescriptorCleaner.c

# copy files in the "data" folder
cp DexWare/bin/$MAINJAR data/
cp DexWare/jni/FileDescriptorCleaner.so data/
