@echo off
REM Compile and run the unit tests (Windows)
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out -cp "lib/*" src\main\java\com\tastetrail\*.java src\main\java\com\tastetrail\model\*.java src\main\java\com\tastetrail\coupon\*.java src\main\java\com\tastetrail\payment\*.java src\main\java\com\tastetrail\service\*.java src\main\java\com\tastetrail\dao\*.java src\main\java\com\tastetrail\security\*.java src\main\java\com\tastetrail\web\*.java src\main\java\com\tastetrail\db\*.java src\test\java\com\tastetrail\*.java
if errorlevel 1 (pause & exit /b 1)
java -cp "out;lib/*" com.tastetrail.CoreTest
pause
