<#
.SYNOPSIS
    Maintainer 3-target build & release pipeline for duit.: Stable, Beta, and PrivateDev (`com.diniy.duit.beta`).
.NOTES
    - Lives strictly on private `personal-edition` (blocked from public pushes by `.git/hooks/pre-push`).
    - All 3 builds share `app/src/main`. Only `PrivateDev` (`assembleBetaDev`) includes Developer Center.
    - Hard guards forbid staging or publishing `-dev` APKs into public release folders or public repos.
    - Never creates keystores, stores passwords, enables billing, or auto-publishes releases.
#>
[CmdletBinding()]
param(
    [ValidateSet("Stable", "Beta", "PrivateDev", "All")]
    [string]$Target = "PrivateDev",
    [string]$VersionName = "1.2.0-dev.1",
    [int]$VersionCode = 0,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sdkDir = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$buildTools = Get-ChildItem (Join-Path $sdkDir "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $buildTools) { throw "Android SDK build-tools missing under $sdkDir" }
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$apkanalyzer = Join-Path $sdkDir "cmdline-tools\latest\bin\apkanalyzer.bat"
$keystore = Join-Path $env:LOCALAPPDATA "duit-beta-signing\duit-beta.p12"
$expectedSignerSha256 = "e26f499ec2524650f5fbb7cf139e62f3de7dd001ad1afbbe1718034a2b785d14"

function Assert-NoPublicPrivateLeak {
    $publicStaging = Get-ChildItem "C:\Users\SV\Documents\duit-release-staging" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "^(release-|publish-)" }
    foreach ($dir in $publicStaging) {
        $leaks = Get-ChildItem $dir.FullName -Recurse -Filter "*-dev*.apk" -ErrorAction SilentlyContinue
        if ($leaks) {
            throw "SAFETY BLOCK: Private -dev APK found inside public staging dir $($dir.FullName): $($leaks.Name -join ', ')"
        }
    }
}

function Invoke-GradleTarget([string[]]$GradleArgs) {
    Push-Location $repoRoot
    try {
        & .\gradlew.bat @GradleArgs --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed for: $($GradleArgs -join ' ')" }
    } finally {
        Pop-Location
    }
}

function Assert-PrivateDevApk([string]$ApkPath, [string]$ExpectedVerName, [int]$ExpectedVerCode) {
    if (-not (Test-Path $ApkPath)) { throw "Missing APK: $ApkPath" }
    $pkg = (& $apkanalyzer manifest application-id $ApkPath).Trim()
    $vn = (& $apkanalyzer manifest version-name $ApkPath).Trim()
    $vc = (& $apkanalyzer manifest version-code $ApkPath).Trim()
    $man = (& $apkanalyzer manifest print $ApkPath) | Out-String
    if ($pkg -ne "com.diniy.duit.beta") { throw "Unexpected package ID: $pkg" }
    if ($vn -ne $ExpectedVerName -or [int]$vc -ne $ExpectedVerCode) {
        throw "Version mismatch: got $vn ($vc), expected $ExpectedVerName ($ExpectedVerCode)"
    }
    if (-not ($man -match "com\.diniy\.duit\.PRIVATE_DEV_EDITION")) { throw "Missing PRIVATE_DEV_EDITION manifest meta-data" }
    if (-not ($man -match "PrivateDevInstallStatusReceiver")) { throw "Missing PrivateDevInstallStatusReceiver in manifest" }
    if (-not ($man -match "DuitBroadcastMessagingService")) { throw "Missing DuitBroadcastMessagingService in manifest" }
    if ($man -match "BetaInstallStatusReceiver") { throw "Public BetaInstallStatusReceiver leaked into PrivateDev APK" }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    $hasDevCenter = $false
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notmatch '^classes\d*\.dex$') { continue }
            $ms = New-Object System.IO.MemoryStream
            $s = $entry.Open()
            try {
                $s.CopyTo($ms)
                $txt = [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($ms.ToArray())
                if ($txt.Contains("Developer Center")) { $hasDevCenter = $true }
                if ($txt.Contains("BetaUpdates")) { throw "Public BetaUpdates leaked into PrivateDev DEX" }
                if ($txt.Contains(("github_pat" + "_11")) -or $txt.Contains(("ghp" + "_"))) { throw "Embedded GitHub token detected in DEX" }
            } finally {
                $ms.Dispose()
                $s.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
    if (-not $hasDevCenter) { throw "Developer Center missing from PrivateDev DEX" }
    Write-Host "[PASS] PrivateDev APK audit clean: $pkg v$vn ($vc)" -ForegroundColor Green
}

Assert-NoPublicPrivateLeak

if ($Target -eq "Stable" -or $Target -eq "All") {
    if ($Target -eq "Stable" -and $PSBoundParameters.ContainsKey("VersionName") -and $VersionName -notmatch "^(\d+)\.(\d+)\.(\d+)$") {
        throw "SAFETY BLOCK: Stable target requires X.Y.Z versionName (no -dev or -beta suffix), refusing: $VersionName"
    }
    $stableVer = if ($Target -eq "Stable" -and $PSBoundParameters.ContainsKey("VersionName")) { $VersionName } else { "1.2.0" }
    $parts = $stableVer.Split(".")
    $stableCode = [int]$parts[0] * 1000000 + [int]$parts[1] * 1000 + [int]$parts[2]
    Invoke-GradleTarget @("testBetaUnitTest", "assembleBeta", "-PduitBetaVersionName=$stableVer", "-PduitBetaVersionCode=$stableCode", "-PduitBetaAppName=duit.")
    Write-Host "[PASS] Public Stable $stableVer ($stableCode) verified + verifyBetaReleaseIsolation PASS" -ForegroundColor Green
}

if ($Target -eq "Beta" -or $Target -eq "All") {
    if ($Target -eq "Beta" -and $PSBoundParameters.ContainsKey("VersionName") -and $VersionName -notmatch "^(\d+)\.(\d+)\.0-beta\.([1-9]\d?)$") {
        throw "SAFETY BLOCK: Beta target requires X.Y.0-beta.N versionName, refusing: $VersionName"
    }
    $betaVer = if ($Target -eq "Beta" -and $PSBoundParameters.ContainsKey("VersionName")) { $VersionName } else { "1.2.0-beta.2" }
    if ($betaVer -notmatch "^(\d+)\.(\d+)\.0-beta\.([1-9]\d?)$") { throw "Invalid beta versionName: $betaVer" }
    $betaCode = [int]$Matches[1] * 1000000 + [int]$Matches[2] * 1000 - 100 + [int]$Matches[3]
    Invoke-GradleTarget @("testBetaUnitTest", "assembleBeta", "-PduitBetaVersionName=$betaVer", "-PduitBetaVersionCode=$betaCode", "-PduitBetaAppName=duit. Beta")
    Write-Host "[PASS] Public Beta $betaVer ($betaCode) verified + verifyBetaReleaseIsolation PASS" -ForegroundColor Green
}

if ($Target -eq "PrivateDev" -or $Target -eq "All") {
    $devVer = if ($VersionName -match "-dev") { $VersionName } else { "1.2.0-dev.1" }
    if ($devVer -notmatch "^(\d+)\.(\d+)\.(\d+)-dev(?:\.([1-9]\d?))?$") {
        throw "PrivateDev versionName must match X.Y.Z-dev[.N], got $devVer"
    }
    $devPatch = if ($Matches[4]) { [int]$Matches[4] } else { 0 }
    $expectedDevCode = [int]$Matches[1] * 10000000 + [int]$Matches[2] * 10000 + [int]$Matches[3] * 100 + $devPatch
    if ($VersionCode -gt 0 -and $VersionCode -ne $expectedDevCode) {
        throw "VersionCode $VersionCode does not match required formula $expectedDevCode"
    }

    Invoke-GradleTarget @(
        "testBetaDevUnitTest",
        "assembleBetaDev",
        "-PduitBetaVersionName=1.2.0",
        "-PduitBetaVersionCode=1002000",
        "-PduitBetaAppName=duit.",
        "-PduitBetaDevVersionName=$devVer",
        "-PduitBetaDevVersionCode=$expectedDevCode"
    )

    $unsignedApk = Join-Path $repoRoot "app\build\outputs\apk\betaDev\app-betaDev-unsigned.apk"
    Assert-PrivateDevApk -ApkPath $unsignedApk -ExpectedVerName $devVer -ExpectedVerCode $expectedDevCode

    $stageDir = Join-Path "C:\Users\SV\Documents\duit-release-staging\private-dev" "v$devVer"
    New-Item -ItemType Directory -Force -Path $stageDir | Out-Null
    $stagedUnsigned = Join-Path $stageDir "duit-$devVer-unsigned.apk"
    $stagedSigned = Join-Path $stageDir "duit-$devVer.apk"
    Copy-Item $unsignedApk $stagedUnsigned -Force

    if ($DryRun) {
        $hash = (Get-FileHash $stagedUnsigned -Algorithm SHA256).Hash
        Write-Host "[DRY-RUN PASS] Staged unsigned PrivateDev APK: $stagedUnsigned (SHA256=$hash)" -ForegroundColor Cyan
        Write-Host "[DRY-RUN] Signing and private GitHub release skipped because -DryRun was passed." -ForegroundColor Cyan
    } else {
        if (-not (Test-Path $keystore)) { throw "Original keystore missing at $keystore" }
        $tmpSigned = Join-Path $env:TEMP ("duit-privatedev-sign-" + [guid]::NewGuid().ToString("N") + ".apk")
        try {
            & $apksigner sign --ks $keystore --ks-key-alias duit-beta --v4-signing-enabled false --out $tmpSigned $stagedUnsigned
            if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
            $certOut = (& $apksigner verify --print-certs $tmpSigned) | Out-String
            if ($certOut -notmatch $expectedSignerSha256) { throw "Signer certificate mismatch!" }
            Move-Item $tmpSigned $stagedSigned -Force
        } finally {
            if (Test-Path $tmpSigned) { Remove-Item $tmpSigned -Force }
        }
        $sha = (Get-FileHash $stagedSigned -Algorithm SHA256).Hash.ToLowerInvariant()
        Write-Host "[SIGNED PASS] $stagedSigned | SHA256=$sha" -ForegroundColor Green
        Write-Host "Next (manual approval): gh release create v$devVer `"$stagedSigned`" -R erlingdiniy/duit-developer-console --title `"duit. v$devVer (Private Developer)`" --notes-file notes.md" -ForegroundColor Yellow
    }
}

Assert-NoPublicPrivateLeak
