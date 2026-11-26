<#
.SYNOPSIS
    One-command local Beta QA workflow for duit.:
    build -> interactive signing -> cert/version verify -> adb install -r -> launch/crash/Room check.
.NOTES
    - Uses an existing PKCS#12 beta keystore and local Sign-duit-Beta.cmd helper.
    - Never creates a new keystore, never reads/stores/prints passwords, and never wipes app data.
    - Configure paths via parameters (-SignScript, -KeystorePath, -StageDir) or environment variables
      (DUIT_BETA_SIGN_SCRIPT, DUIT_BETA_KEYSTORE, DUIT_BETA_STAGE_DIR).
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [switch]$SkipBuild,
    [string]$SignScript,
    [string]$KeystorePath,
    [string]$StageDir
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$packageId = "com.diniy.duit.beta"
$launchActivity = "$packageId/com.diniy.duit.MainActivity"
$keystorePath = if ($KeystorePath) {
    $KeystorePath
} elseif ($env:DUIT_BETA_KEYSTORE) {
    $env:DUIT_BETA_KEYSTORE
} else {
    Join-Path $env:LOCALAPPDATA "duit-beta-signing\duit-beta.p12"
}
$stageDir = if ($StageDir) {
    $StageDir
} elseif ($env:DUIT_BETA_STAGE_DIR) {
    $env:DUIT_BETA_STAGE_DIR
} else {
    Join-Path $env:LOCALAPPDATA "duit-beta-signing\staging\latest-qa"
}

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Get-SdkDir {
    $localProps = Join-Path $repoRoot "local.properties"
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            return ($line -replace '^sdk\.dir=', '' -replace '\\\\', '\' -replace '\\:', ':').Trim()
        }
    }
    return Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

function Resolve-SignScript {
    if ($SignScript) {
        if (Test-Path $SignScript) { return $SignScript }
        Fail "Sign script not found: $SignScript"
    }
    if ($env:DUIT_BETA_SIGN_SCRIPT) {
        if (Test-Path $env:DUIT_BETA_SIGN_SCRIPT) { return $env:DUIT_BETA_SIGN_SCRIPT }
        Fail "Sign script from DUIT_BETA_SIGN_SCRIPT not found: $($env:DUIT_BETA_SIGN_SCRIPT)"
    }
    $defaultScript = Join-Path $env:LOCALAPPDATA "duit-beta-signing\Sign-duit-Beta.cmd"
    if (Test-Path $defaultScript) { return $defaultScript }
    Fail "Sign-duit-Beta.cmd not found. Pass -SignScript <path> or set DUIT_BETA_SIGN_SCRIPT."
}

function Resolve-DeviceSerial([string]$AdbExe) {
    if ($Serial) { return $Serial }
    $lines = & $AdbExe devices | Where-Object { $_ -match '\tdevice$' }
    if (-not $lines) { Fail "No authorized ADB device connected." }
    $ids = @($lines | ForEach-Object { ($_ -split '\t')[0].Trim() })
    # Prefer USB transport when both USB and wireless ADB are active for the same phone.
    $usb = $ids | Where-Object { $_ -notmatch '_adb-tls-connect' } | Select-Object -First 1
    if ($usb) { return $usb }
    return $ids[0]
}

function Get-CertSha256([string]$ApkSigner, [string]$ApkPath) {
    $out = & $ApkSigner verify --print-certs $ApkPath 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "apksigner verify failed for $ApkPath`n$out" }
    $match = $out | Select-String "Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)" | Select-Object -First 1
    if (-not $match) { Fail "Could not read SHA-256 digest from $ApkPath" }
    return $match.Matches[0].Groups[1].Value.ToLowerInvariant()
}

# 1. Pre-flight checks
if (-not (Test-Path $keystorePath)) {
    Fail "Original beta keystore missing at $keystorePath. Refusing to generate a new keystore."
}
$signScriptPath = Resolve-SignScript
$sdkDir = Get-SdkDir
$adb = Join-Path $sdkDir "platform-tools\adb.exe"
$buildToolsDir = Get-ChildItem (Join-Path $sdkDir "build-tools") -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildToolsDir) { Fail "Android SDK build-tools not found under $sdkDir." }
$apksigner = Join-Path $buildToolsDir.FullName "apksigner.bat"
$aapt2 = Join-Path $buildToolsDir.FullName "aapt2.exe"
foreach ($tool in @($adb, $apksigner, $aapt2)) {
    if (-not (Test-Path $tool)) { Fail "Required tool missing: $tool" }
}
$device = Resolve-DeviceSerial $adb
Write-Host "[INFO] Device: $device | Keystore: OK (existing)" -ForegroundColor Cyan

# 2. Build unsigned beta APK
$unsignedApk = Join-Path $repoRoot "app\build\outputs\apk\beta\app-beta-unsigned.apk"
if (-not $SkipBuild) {
    Write-Host "[1/5] Building unsigned beta APK (:app:assembleBeta)..." -ForegroundColor Cyan
    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot "gradlew.bat") ":app:assembleBeta"
        if ($LASTEXITCODE -ne 0) { Fail "Gradle :app:assembleBeta failed." }
    } finally {
        Pop-Location
    }
}
if (-not (Test-Path $unsignedApk)) { Fail "Unsigned APK not found: $unsignedApk" }

# 3. Stage & run existing Sign-duit-Beta.cmd interactively
Write-Host "[2/5] Launching Sign-duit-Beta.cmd (enter keystore password, then press any key when done)..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $stageDir | Out-Null
$stagedUnsigned = Join-Path $stageDir "duit-beta-1-UNSIGNED.apk"
$stagedSigned = Join-Path $stageDir "duit-beta-1.apk"
$stagedCmd = Join-Path $stageDir "Sign-duit-Beta.cmd"
if (Test-Path $stagedSigned) { Remove-Item $stagedSigned -Force }
Copy-Item $unsignedApk $stagedUnsigned -Force
Copy-Item $signScriptPath $stagedCmd -Force

if ([Console]::IsInputRedirected) {
    Write-Host "[INFO] Opened 'Sign duit. Beta' window for secure password entry..." -ForegroundColor Yellow
    $signProc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "`"$stagedCmd`"" -WorkingDirectory $stageDir -Wait -PassThru
    $signExit = $signProc.ExitCode
} else {
    & cmd.exe /c "`"$stagedCmd`""
    $signExit = $LASTEXITCODE
}
if ($signExit -ne 0 -or -not (Test-Path $stagedSigned)) {
    Fail "Interactive signing did not produce $stagedSigned."
}

# 4. Verify package, versionCode, Developer Center isolation, and certificate against installed beta
Write-Host "[3/5] Verifying APK package, versionCode, release isolation, and certificate..." -ForegroundColor Cyan
$badging = & $aapt2 dump badging $stagedSigned 2>&1 | Select-String "^package:" | Select-Object -First 1
if (-not $badging -or $badging.Line -notmatch "name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'") {
    Fail "Could not parse package badging from $stagedSigned"
}
$newPkg = $Matches[1]
$newVersionCode = [int]$Matches[2]
$newVersionName = $Matches[3]
if ($newPkg -ne $packageId) { Fail "Unexpected package '$newPkg' (expected '$packageId')." }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$forbiddenBetaMarkers = @(
    "com/diniy/duit/devcenter",
    "DevCenter",
    "devcenter",
    "DeveloperCenter",
    "Developer Center",
    "duit_dev_center_prefs",
    "duit_dev_center_pat_v1",
    "duit_dev_center_pin_v1",
    "duit_dev_center_sheets_v1",
    "custom-broadcast.yml",
    "ENABLE_CUSTOM_FCM_DISPATCH",
    "QA_FCM_TOKEN_COPY"
)
$zipArchive = [System.IO.Compression.ZipFile]::OpenRead($stagedSigned)
try {
    foreach ($entry in $zipArchive.Entries) {
        if ($entry.FullName -eq "AndroidManifest.xml" -or $entry.FullName -match '^res/' -or $entry.FullName -match '^classes\d*\.dex$') {
            if ($entry.Length -le 0) { continue }
            $stream = $entry.Open()
            $ms = New-Object System.IO.MemoryStream
            try {
                $stream.CopyTo($ms)
                $rawText = [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($ms.ToArray())
                foreach ($marker in $forbiddenBetaMarkers) {
                    if ($rawText.Contains($marker)) {
                        Fail "Public beta APK leaked forbidden Developer Center marker '$marker' in $($entry.FullName)."
                    }
                }
            } finally {
                $ms.Dispose()
                $stream.Dispose()
            }
        }
    }
} finally {
    $zipArchive.Dispose()
}

$newSha = Get-CertSha256 $apksigner $stagedSigned
$pmPath = (& $adb -s $device shell pm path $packageId 2>&1 | Select-String "^package:" | Select-Object -First 1)
if ($pmPath) {
    $remoteApk = ($pmPath.Line -replace '^package:', '').Trim()
    $tmpInstalled = Join-Path $env:TEMP ("duit-installed-verify-" + [guid]::NewGuid().ToString("N") + ".apk")
    try {
        & $adb -s $device pull $remoteApk $tmpInstalled | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "Failed to pull installed APK for certificate verification." }
        $installedSha = Get-CertSha256 $apksigner $tmpInstalled
        if ($newSha -ne $installedSha) {
            Fail "CERTIFICATE MISMATCH! Stopping before install.`n  New:       $newSha`n  Installed: $installedSha"
        }
    } finally {
        Remove-Item $tmpInstalled -Force -ErrorAction SilentlyContinue
    }
    $verLine = (& $adb -s $device shell dumpsys package $packageId 2>&1 | Select-String "versionCode=(\d+)" | Select-Object -First 1)
    if ($verLine -and $verLine.Line -match "versionCode=(\d+)") {
        $installedVersionCode = [int]$Matches[1]
        if ($newVersionCode -lt $installedVersionCode) {
            Fail "VERSION DOWNGRADE ($newVersionCode < installed $installedVersionCode). Stopping to preserve data."
        }
    }
    Write-Host "[PASS] Cert SHA-256 matches installed $packageId ($newSha), versionCode=$newVersionCode ($newVersionName)" -ForegroundColor Green
} else {
    Write-Host "[INFO] $packageId not currently installed on $device; new cert SHA-256=$newSha" -ForegroundColor Yellow
}

# 5. Install with -r (preserve data) + Launch & Crash/Migration check
Write-Host "[4/5] Installing with adb install -r (preserving app data)..." -ForegroundColor Cyan
$installOut = & $adb -s $device install -r $stagedSigned 2>&1
if ($LASTEXITCODE -ne 0) { Fail "adb install -r failed:`n$installOut" }

Write-Host "[5/5] Launching app and checking crashes / Room migration..." -ForegroundColor Cyan
& $adb -s $device logcat -c
$launchOut = (& $adb -s $device shell am start -S -W -n $launchActivity 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0 -or ($launchOut -notmatch "Status:\s*ok")) {
    Fail "Launch failed:`n$launchOut"
}
Start-Sleep -Seconds 4
$appPid = (& $adb -s $device shell pidof $packageId 2>&1).Trim()
if (-not $appPid) { Fail "App process not running after launch (possible crash)." }

$pidLogs = & $adb -s $device logcat -d --pid=$appPid 2>&1 |
    Select-String -Pattern "AndroidRuntime|FATAL|Exception|SQLite|Room|Migration" -CaseSensitive:$false
$globalFatal = & $adb -s $device logcat -d *:E 2>&1 |
    Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime.*$packageId" -CaseSensitive:$false

if ($pidLogs -or $globalFatal) {
    Write-Host "[FAIL] Crash/DB errors detected:" -ForegroundColor Red
    $globalFatal | Select-Object -First 15 | ForEach-Object { Write-Host $_.Line }
    $pidLogs | Select-Object -First 25 | ForEach-Object { Write-Host $_.Line }
    exit 1
}

$totalTime = ($launchOut | Select-String "TotalTime:\s*(\d+)" | ForEach-Object { $_.Matches[0].Groups[1].Value }) | Select-Object -First 1
Write-Host ""
Write-Host "=== BETA QA PASS ===" -ForegroundColor Green
Write-Host "APK:     $stagedSigned ($packageId v$newVersionName / $newVersionCode)"
Write-Host "Cert:    MATCH ($newSha)"
Write-Host "Install: adb install -r Success (data preserved)"
Write-Host "Launch:  OK (${totalTime}ms, PID $appPid) | Crashes: 0 | Room/SQLite errors: 0"
