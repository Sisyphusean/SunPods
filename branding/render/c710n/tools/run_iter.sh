#!/bin/bash
# One review iteration: boards (buds and case), overlays, reference sheets, feature sheet.
#   bash run_iter.sh <NN>
set -e
cd "$(dirname "$0")"
IT=../iter$1
mkdir -p $IT
node board.mjs $IT all > $IT/board.log 2>&1
node features.mjs $IT > /dev/null 2>&1 || node features.mjs $IT > /dev/null 2>&1
python compose_board.py $IT buds "iteration $1 earbuds"
python compose_board.py $IT case "iteration $1 case"
python compose_features.py $IT
grep -v "^\[page" $IT/board.log | cut -c1-70
