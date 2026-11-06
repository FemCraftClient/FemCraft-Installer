@echo off
cd /d "%~dp0"
if exist classes rmdir /s /q classes
mkdir classes
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 --release 17 -d classes @sources.txt || (del sources.txt & pause & exit /b 1)
del sources.txt
xcopy /e /i /q assets classes\assets >nul
if not exist ..\build mkdir ..\build
jar --create --file ..\build\FemCraftInstaller.jar --main-class femboys.are.cute.installer.Main -C classes .
rmdir /s /q classes
pushd ..\build
if exist FemCraftInstaller-Windows.zip del FemCraftInstaller-Windows.zip
if exist FemCraftInstaller-Other.zip del FemCraftInstaller-Other.zip
tar -a -cf FemCraftInstaller-Windows.zip FemCraftInstaller.jar FemCraftInstaller.bat
tar -a -cf FemCraftInstaller-Other.zip FemCraftInstaller.jar FemCraftInstaller.sh
popd
echo Built FemCraftInstaller.jar, FemCraftInstaller-Windows.zip and FemCraftInstaller-Other.zip
pause
