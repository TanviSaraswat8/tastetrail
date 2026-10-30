@echo off
REM TasteTrail - compile and start the server on http://localhost:8080 (Windows)
cd /d "%~dp0"
if not exist out mkdir out
if not exist data mkdir data
echo Compiling...
javac -encoding UTF-8 -d out -cp "lib/*" src\main\java\com\tastetrail\*.java src\main\java\com\tastetrail\model\*.java src\main\java\com\tastetrail\coupon\*.java src\main\java\com\tastetrail\payment\*.java src\main\java\com\tastetrail\service\*.java src\main\java\com\tastetrail\dao\*.java src\main\java\com\tastetrail\security\*.java src\main\java\com\tastetrail\web\*.java src\main\java\com\tastetrail\db\*.java
if errorlevel 1 (echo. & echo Compilation failed - is a JDK 17+ installed? Try: java -version & pause & exit /b 1)
java -cp "out;lib/*" com.tastetrail.Main %*
pause
