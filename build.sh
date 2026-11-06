#!/bin/sh
cd "$(dirname "$0")" || exit 1
rm -rf classes && mkdir classes
javac -encoding UTF-8 --release 17 -d classes $(find src -name '*.java') || exit 1
cp -r assets classes/
mkdir -p ../build
jar --create --file ../build/FemCraftInstaller.jar --main-class femboys.are.cute.installer.Main -C classes .
rm -rf classes
cd ../build || exit 1
rm -f FemCraftInstaller-Windows.zip FemCraftInstaller-Other.zip
zip -q FemCraftInstaller-Windows.zip FemCraftInstaller.jar FemCraftInstaller.bat
zip -q FemCraftInstaller-Other.zip FemCraftInstaller.jar FemCraftInstaller.sh
echo "Built FemCraftInstaller.jar, FemCraftInstaller-Windows.zip and FemCraftInstaller-Other.zip"
