REM Environment
set CURRENT_DIR=%~dp0
set TEMP=%CURRENT_DIR%\temp
set PUBLIC=%CURRENT_DIR%\public

echo %CURRENT_DIR%
echo %TEMP%
echo %PUBLIC%

REM Make temp
if exist %TEMP% (
  rmdir /s /q %TEMP% > nul
)
mkdir %TEMP%

REM Make public dir
if exist %PUBLIC% (
  rmdir /s /q %PUBLIC% > nul
)
mkdir %PUBLIC%

REM ### make Russian

REM Copy config
cp mkdocs.yml %TEMP%
REM Copy docs
mkdir %TEMP%\docs
xcopy docs %TEMP%\docs /s /e
REM Remove en
rmdir /s /q %TEMP%\docs\en
REM Make docs
pushd %TEMP%
mkdocs build
popd
REM Copy to public
xcopy %TEMP%\site %PUBLIC% /s /e

REM Clear temp
if exist %TEMP% (
  rmdir /s /q %TEMP% > nul
)
mkdir %TEMP%

REM ### make Endlish

REM Make public en dir
mkdir %PUBLIC%\en
REM Copy config
cp mkdocs.en.yml %TEMP%\mkdocs.yml
REM Copy docs
mkdir %TEMP%\docs
xcopy docs\en %TEMP%\docs /s /e
REM Copy assets
mkdir %TEMP%\docs\assets
xcopy docs\assets %TEMP%\docs\assets /s /e
REM Make docs
pushd %TEMP%
mkdocs build
popd
REM Copy to public
xcopy %TEMP%\site %PUBLIC%\en /s /e