<#
  Scritto's "flutter run": builds the debug app, installs it and launches it.

    .\run.ps1                 # use the running device/emulator, or start the emulator
    .\run.ps1 -Clean          # clean build first
    .\run.ps1 -Avd Pixel_8    # pick a different emulator
#>
param(
    [string]$Avd = "Medium_Phone_API_36.1",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$emulator = "$env:ANDROID_HOME\emulator\emulator.exe"

function Connected-Device { (& $adb devices) | Select-String "\tdevice$" }

# 1. Need a device: plug in a phone, or boot the emulator.
if (-not (Connected-Device)) {
    Write-Host "No device found - starting emulator '$Avd'..." -ForegroundColor Yellow
    # An explicit DNS server keeps the emulator online (Scritto AI needs internet).
    Start-Process $emulator -ArgumentList "-avd", $Avd, "-dns-server", "8.8.8.8,1.1.1.1", "-no-boot-anim"
    & $adb wait-for-device
    while ((& $adb shell getprop sys.boot_completed 2>$null) -notmatch "1") { Start-Sleep -Seconds 3 }
    Start-Sleep -Seconds 5
}

# 2. Build + install.
if ($Clean) { .\gradlew.bat clean }
.\gradlew.bat :app:installDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed - see the error above." }

# 3. Permissions the app asks for anyway (skips the prompts while developing) + launch.
& $adb shell pm grant com.internship.scritto android.permission.POST_NOTIFICATIONS 2>$null
& $adb shell pm grant com.internship.scritto android.permission.RECORD_AUDIO 2>$null
& $adb shell am start -n com.internship.scritto/.MainActivity | Out-Null

Write-Host "Scritto is running." -ForegroundColor Green
