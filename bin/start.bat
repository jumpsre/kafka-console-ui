@echo off

rem MAIN_CLASS=org.springframework.boot.loader.JarLauncher
rem java version more than jdk 1.8

setlocal enabledelayedexpansion

set "BIN_DIR=%~dp0"
if not "%BIN_DIR:~-1%"=="\" set "BIN_DIR=%BIN_DIR%\"

set "BASE_DIR=%BIN_DIR%.."
for %%I in ("%BASE_DIR%") do set "BASE_DIR=%%~fI"

if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java"
) else (
    echo ERROR: JAVA_HOME is not defined
    exit /b 1
)

set "JAVA_OPTS=-Xmx512m -Xms512m -Xmn256m -Xss256k -Dfile.encoding=utf-8"

set "JAVA_OPTS=%JAVA_OPTS% --add-opens java.base/java.io=ALL-UNNAMED"
set "JAVA_OPTS=%JAVA_OPTS% --add-opens java.base/java.lang=ALL-UNNAMED"
set "JAVA_OPTS=%JAVA_OPTS% --add-opens java.base/java.util=ALL-UNNAMED"
set "JAVA_OPTS=%JAVA_OPTS% --add-opens java.base/java.net=ALL-UNNAMED"
set "JAVA_OPTS=%JAVA_OPTS% --add-opens java.security.jgss/sun.security.jgss=ALL-UNNAMED"

set "CONFIG_FILE=%BASE_DIR%\config\application.yml"
set "TARGET=%BASE_DIR%\lib\kafka-console-ui.jar"
set "DATA_DIR=%BASE_DIR%"
set "LOG_HOME=%BASE_DIR%"

rem Kerberos 支持：如果 config\krb5.conf 存在则注入
set "KRB5_CONF=%BASE_DIR%\config\krb5.conf"
if exist "%KRB5_CONF%" (
    echo Found krb5.conf, enable Kerberos: %KRB5_CONF%
    set "JAVA_OPTS=%JAVA_OPTS% -Djava.security.krb5.conf=%KRB5_CONF%"
)
set "JAVA_OPTS=%JAVA_OPTS% -Dsun.security.krb5.debug=false"

if not exist "%TARGET%" (
    echo ERROR: Jar file not found at [%TARGET%]
    exit /b 1
)
if not exist "%CONFIG_FILE%" (
    echo WARNING: Config file not found at [%CONFIG_FILE%]
)

"%JAVA_CMD%" %JAVA_OPTS% -jar "%TARGET%" --spring.config.location="%CONFIG_FILE%" --data.dir="%DATA_DIR%" --logging.home="%LOG_HOME%"

endlocal