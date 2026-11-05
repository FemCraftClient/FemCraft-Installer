#!/bin/sh
cd "$(dirname "$0")" || exit 1
rm -rf classes && mkdir classes
javac -encoding UTF-8 --release 17 -d classes $(find src -name '*.java') || exit 1
cp -r assets classes/
mkdir -p ../build
jar --create --file ../build/FemCraftInstaller.jar --main-class femboys.are.cute.installer.Main -C classes .
rm -rf classes
echo "Built ../build/FemCraftInstaller.jar"
