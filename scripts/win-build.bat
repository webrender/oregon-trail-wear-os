@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=C:\Program Files\Git\usr\bin;%PATH%
cd /d %~dp0\..
call gradlew.bat %*
