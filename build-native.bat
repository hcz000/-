@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64
set JAVA_HOME=C:\Users\lenovoa\scoop\apps\graalvm21-jdk21\current
set PATH=%JAVA_HOME%\bin;%PATH%
cd D:\IdeaProjects\demo1
echo [1/3] Generate Spring AOT assets...
call mvn "-Dmaven.test.skip=true" spring-boot:process-aot -Pnative
if not exist target\spring-aot\main\classes\com\example\demo\Demo1Application__ApplicationContextInitializer.class (
    echo ERROR: Spring AOT initializer not found. Abort native build.
    exit /b 1
)
echo [2/3] Merge Spring AOT classes/resources into target\classes...
if exist target\spring-aot\main\classes (
    xcopy /E /I /Y target\spring-aot\main\classes\* target\classes\ >nul
)
if exist target\spring-aot\main\resources (
    xcopy /E /I /Y target\spring-aot\main\resources\* target\classes\ >nul
)
echo [3/3] Build native executable...
mvn native:compile-no-fork -Pnative
