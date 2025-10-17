$ErrorActionPreference = "Stop"

# --- 0) опционально: закрыть Gradle-демоны VS Code (если открыты) ---
# Это безопасно: только останавливает фоновые демоны (не Android Studio)
try { & gradle --stop } catch {}

# --- 1) параметры дистрибутива Gradle ---
$ver="8.9"
$zip="gradle-$ver-bin.zip"
$url="https://services.gradle.org/distributions/$zip"

# --- 2) уникальная временная папка, чтобы не конфликтовать с занятым %TEMP% ---
$tmp = Join-Path $env:TEMP ("gradle_bootstrap_" + [guid]::NewGuid())
New-Item -ItemType Directory -Force $tmp | Out-Null
$dst = Join-Path $tmp $zip

Write-Host "Downloading $url -> $dst"
Invoke-WebRequest -Uri $url -OutFile $dst

# --- 3) распакуем в %USERPROFILE%\gradle\gradle-8.9 ---
$root="$env:USERPROFILE\gradle"
New-Item -ItemType Directory -Force $root | Out-Null
Expand-Archive -Path $dst -DestinationPath $root -Force

# --- 4) добавим Gradle в PATH на ЭТУ сессию и проверим ---
$env:PATH = "$root\gradle-$ver\bin;$env:PATH"
& gradle -v

# --- 5) подчистим возможный битый wrapper в проекте ---
# (если файлов нет  ошибок не будет)
Remove-Item -Recurse -Force ".\gradle\wrapper" -ErrorAction SilentlyContinue
Remove-Item -Force ".\gradlew" -ErrorAction SilentlyContinue
Remove-Item -Force ".\gradlew.bat" -ErrorAction SilentlyContinue

# --- 6) сгенерируем свежий wrapper ---
& gradle wrapper --gradle-version $ver --distribution-type bin

# --- 7) почистим временную папку ---
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue

Write-Host "Wrapper generated. Now use .\gradlew from the project root."
