#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR

MAINJAR=$DIR/data/dexware.jar

TMPDIR=/tmp/dexware
rm -rf $TMPDIR
mkdir -p $TMPDIR

CMD="./dalvik/dalvik -cp $MAINJAR DexWare"
echo "CMD: $CMD"
$CMD
