@echo off

@EXTRA_VARS@

@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set HOME=%HOMEDRIVE%%HOMEPATH%)

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome

for /f %%j in ("java.exe") do (
  set JAVA_EXE="%%~$PATH:j"
  goto init
)

:OkJHome
if exist "%JAVA_HOME%\bin\java.exe" (
 SET JAVA_EXE="%JAVA_HOME%\bin\java.exe"
 goto init
)

echo.
echo ERROR: JAVA_HOME is set to an invalid directory.
echo JAVA_HOME = %JAVA_HOME%
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation
echo.
goto error

:init
@REM Decide how to startup depending on the version of windows
set RAW_ARGS=

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM -- 4NT shell
if "%@eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
@REM Pass the arguments straight through as %* below, rather than round-tripping
@REM them through a variable: that needed a second round of percent expansion,
@REM which mangles arguments containing % ^ or &.
set RAW_ARGS=1
goto endInit

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto endInit

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto endInit
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

@REM Reaching here means variables are defined and arguments have been captured
:endInit

set JAR_PATH=@JAR_PATH@
SET PROG_DIR=%~dp0
SET PSEP=;

@REM Start Java program
:runm2
SET RUN_CMD=%JAVA_EXE% @JVM_OPTS@ %JAVA_OPTS% -Dprog.dir="%PROG_DIR:\=\\%" -jar "%JAR_PATH%"
if "%RAW_ARGS%"=="1" goto rawExec
%RUN_CMD% %CMD_LINE_ARGS%
goto runDone
:rawExec
%RUN_CMD% %*
:runDone
SET ERROR_CODE=%ERRORLEVEL%
if %ERROR_CODE% NEQ 0 goto error
goto end

:error
if "%OS%"=="Windows_NT" endlocal & set ERROR_CODE=%ERROR_CODE%
if %ERROR_CODE% EQU 0 set ERROR_CODE=1

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set JAVA_EXE=
set CMD_LINE_ARGS=
set RAW_ARGS=
set RUN_CMD=
set PSEP=
goto postExec

:endNT
@endlocal

:postExec
exit /B %ERROR_CODE%
