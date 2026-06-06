@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@echo off
@setlocal

set ERROR_CODE=0
set MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
if not "%MAVEN_PROJECTBASEDIR%"=="" goto endDetectBaseDir
set EXEC_DIR=%CD%
set WDIR=%EXEC_DIR%
:findBaseDir
if exist "%WDIR%\.mvn" goto baseDirFound
cd ..
if "%WDIR%"=="%CD%" goto baseDirNotFound
set WDIR=%CD%
goto findBaseDir
:baseDirFound
set MAVEN_PROJECTBASEDIR=%WDIR%
cd "%EXEC_DIR%"
goto endDetectBaseDir
:baseDirNotFound
set MAVEN_PROJECTBASEDIR=%EXEC_DIR%
cd "%EXEC_DIR%"
:endDetectBaseDir

set MAVEN_USER_HOME=%MAVEN_USER_HOME%
if "%MAVEN_USER_HOME%"=="" set MAVEN_USER_HOME=%USERPROFILE%\.m2

set WRAPPER_DIR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper
set WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties
set MAVEN_VERSION=3.9.9
set MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%
set MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd

if exist "%MAVEN_BIN%" goto runMaven

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$props=ConvertFrom-StringData ((Get-Content -Raw '%WRAPPER_PROPS%') -replace '\\:','\:');" ^
  "$url=$props.distributionUrl;" ^
  "$zip=Join-Path '%MAVEN_USER_HOME%' 'wrapper\dists\apache-maven-%MAVEN_VERSION%.zip';" ^
  "New-Item -ItemType Directory -Force (Split-Path $zip) | Out-Null;" ^
  "New-Item -ItemType Directory -Force '%MAVEN_HOME%' | Out-Null;" ^
  "Invoke-WebRequest -Uri $url -OutFile $zip;" ^
  "$tmp=Join-Path '%MAVEN_USER_HOME%' 'wrapper\dists\apache-maven-%MAVEN_VERSION%-tmp';" ^
  "if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp };" ^
  "Expand-Archive -Path $zip -DestinationPath $tmp -Force;" ^
  "$expanded=Get-ChildItem $tmp | Select-Object -First 1;" ^
  "Copy-Item -Recurse -Force (Join-Path $expanded.FullName '*') '%MAVEN_HOME%';" ^
  "Remove-Item -Recurse -Force $tmp"
if ERRORLEVEL 1 goto error

:runMaven
"%MAVEN_BIN%" %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
exit /B %ERROR_CODE%
