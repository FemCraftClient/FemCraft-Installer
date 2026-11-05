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
echo Built ..\build\FemCraftInstaller.jar
pause
