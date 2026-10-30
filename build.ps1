# Builds the debug APK and copies it to P-Vault.apk next to this script.
$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "$env:LOCALAPPDATA\DudeTools\jdk\jdk-21.0.12.1+1"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Push-Location $PSScriptRoot
try {
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
    Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "P-Vault.apk" -Force
    Write-Host "APK ready: $PSScriptRoot\P-Vault.apk"
} finally {
    Pop-Location
}
