<#
.SYNOPSIS
    Maintainer synchronized 3-target build & release pipeline for duit.: Stable, Beta, PrivateDev, or Synchronized (`com.diniy.duit.beta`).
.NOTES
    - Lives strictly on private `personal-edition` (blocked from public pushes by `.git/hooks/pre-push`).
    - Enforces `main` ancestry on `personal-edition` so every paired Stable & PrivateDev build shares synchronized shared code.
    - All 3 builds share `app/src/main`. Only `PrivateDev` (`assembleBetaDev`) includes Developer Center.
    - Hard guards forbid staging `-dev` APKs into public folders, staging public APKs into `private-dev`, or triggering public notifications for `-dev`.
    - Never creates keystores, stores passwords, enables billing, or auto-publishes releases.
#>
[CmdletBinding()]
param(
    [ValidateSet("Stable", "Beta", "PrivateDev", "Synchronized", "All")]
    [string]$Target = "PrivateDev",
    [string]$VersionName = "1.3.0",
    [int]$VersionCode = 0,
    [ValidateRange(1, 99)]
    [int]$DevPatch = 1,
    [switch]$DryRun,
    [switch]$UploadCrashlyticsMapping
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

function Assert-BranchSynchronized {
    Push-Location $repoRoot
    try {
        $branch = (& git rev-parse --abbrev-ref HEAD).Trim()
        if ($branch -eq "personal-edition") {
            & git merge-base --is-ancestor main HEAD
            if ($LASTEXITCODE -ne 0) {
                throw "SAFETY BLOCK: 'personal-edition' is missing commits from 'main'. Merge 'main' into 'personal-edition' first."
            }
        }
        $remoteUrls = (& git remote -v) | Out-String
        if ($branch -eq "personal-edition" -and ($remoteUrls -notmatch "no_push|erlingdiniy/duit")) {
            Write-Host "[WARN] Inspect git remotes carefully before any push." -ForegroundColor Yellow
        }
    } finally {
        Pop-Location
    }
}

function Assert-NoPublicPrivateLeak {
    $stagingRoot = "C:\Users\SV\Documents\duit-release-staging"
    $publicStaging = Get-ChildItem $stagingRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "^(release-|publish-|public-)" }
    foreach ($dir in $publicStaging) {
        $leaks = Get-ChildItem $dir.FullName -Recurse -Filter "*-dev*.apk" -ErrorAction SilentlyContinue
        if ($leaks) {
            throw "SAFETY BLOCK: Private -dev APK found inside public staging dir $($dir.FullName): $($leaks.Name -join ', ')"
        }
    }
    $privateDir = Join-Path $stagingRoot "private-dev"
    if (Test-Path $privateDir) {
        $publicLeaks = Get-ChildItem $privateDir -Recurse -Filter "*.apk" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch "-dev" }
        if ($publicLeaks) {
            throw "SAFETY BLOCK: Non-dev public APK found inside private staging dir ${privateDir}: $($publicLeaks.Name -join ', ')"
        }
    }
}

function Invoke-GradleTarget([string[]]$GradleArgs) {
    Push-Location $repoRoot
    try {
        & .\gradlew.bat @GradleArgs --no-daemon --max-workers=2
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed for: $($GradleArgs -join ' ')" }
    } finally {
        Pop-Location
    }
}

function Assert-CommonPrivacyManifest([string]$ManifestText, [string]$Label) {
    if ($ManifestText -notmatch 'android:allowBackup="false"') {
        throw "${Label}: missing android:allowBackup=`"false`""
    }
    if ($ManifestText -notmatch 'android:usesCleartextTraffic="false"') {
        throw "${Label}: missing android:usesCleartextTraffic=`"false`""
    }
    if ($ManifestText -match 'com\.google\.android\.gms\.permission\.AD_ID|ACCESS_ADSERVICES_') {
        throw "${Label}: unexpected advertising permission in merged manifest"
    }
    foreach ($metaCheck in @(
        @{ Name = "firebase_analytics_collection_enabled"; Value = "false" },
        @{ Name = "firebase_crashlytics_collection_enabled"; Value = "false" },
        @{ Name = "google_analytics_adid_collection_enabled"; Value = "false" }
    )) {
        $pattern = "android:name=`"$($metaCheck.Name)`"[^>]*android:value=`"$($metaCheck.Value)`""
        if ($ManifestText -notmatch $pattern) {
            throw "${Label}: missing opt-in/privacy meta-data $($metaCheck.Name)=$($metaCheck.Value)"
        }
    }
}

function Scan-ApkMarkers([string]$ApkPath, [string[]]$ForbiddenMarkers, [string[]]$RequiredDexMarkers, [string]$Label) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
    $foundRequired = @{}
    foreach ($req in $RequiredDexMarkers) { $foundRequired[$req] = $false }
    try {
        foreach ($entry in $zip.Entries) {
            $isAudited = ($entry.FullName -eq "AndroidManifest.xml") -or
                ($entry.FullName -match '^res/') -or
                ($entry.FullName -match '^classes\d*\.dex$')
            if (-not $isAudited -or $entry.Length -le 0) { continue }
            $ms = New-Object System.IO.MemoryStream
            $s = $entry.Open()
            try {
                $s.CopyTo($ms)
                $txt = [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($ms.ToArray())
                foreach ($req in $RequiredDexMarkers) {
                    if ($txt.Contains($req)) { $foundRequired[$req] = $true }
                }
                foreach ($bad in $ForbiddenMarkers) {
                    if ($txt.Contains($bad)) {
                        throw "${Label}: forbidden marker '$bad' detected in $($entry.FullName)"
                    }
                }
            } finally {
                $ms.Dispose()
                $s.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
    foreach ($req in $RequiredDexMarkers) {
        if (-not $foundRequired[$req]) {
            throw "${Label}: required marker '$req' missing from DEX/resources"
        }
    }
}

function Assert-PublicApk([string]$ApkPath, [string]$ExpectedVerName, [int]$ExpectedVerCode) {
    if (-not (Test-Path $ApkPath)) { throw "Missing Public APK: $ApkPath" }
    $pkg = (& $apkanalyzer manifest application-id $ApkPath).Trim()
    $vn = (& $apkanalyzer manifest version-name $ApkPath).Trim()
    $vc = (& $apkanalyzer manifest version-code $ApkPath).Trim()
    $man = (& $apkanalyzer manifest print $ApkPath) | Out-String
    if ($pkg -ne "com.diniy.duit.beta") { throw "Unexpected public package ID: $pkg" }
    if ($vn -ne $ExpectedVerName -or [int]$vc -ne $ExpectedVerCode) {
        throw "Public version mismatch: got $vn ($vc), expected $ExpectedVerName ($ExpectedVerCode)"
    }
    if (-not ($man -match "BetaInstallStatusReceiver")) { throw "Missing BetaInstallStatusReceiver in public manifest" }
    if (-not ($man -match "DuitBroadcastMessagingService")) { throw "Missing DuitBroadcastMessagingService in public manifest" }
    foreach ($privateMan in @("PRIVATE_DEV_EDITION", "PrivateDevInstallStatusReceiver", "DevAnalyticsOAuthRedirectActivity")) {
        if ($man -match $privateMan) { throw "Private marker '$privateMan' leaked into public manifest" }
    }
    Assert-CommonPrivacyManifest -ManifestText $man -Label "Public APK"

    $forbidden = @(
        "com/diniy/duit/devcenter", "DevCenter", "devcenter", "DeveloperCenter", "Developer Center",
        "PRIVATE_DEV_EDITION", "PrivateDevUpdates", "PrivateDevUpdateModel", "PrivateDevInstallStatusReceiver",
        "DevCenterAnalyticsOAuth", "DevAnalyticsOAuthRedirectActivity", "oauth2.googleapis.com",
        "duit_qa_crash", "duit_qa_analytics", ("github_pat" + "_11"), ("ghp" + "_")
    )
    Scan-ApkMarkers -ApkPath $ApkPath -ForbiddenMarkers $forbidden -RequiredDexMarkers @("duit-beta-install") -Label "Public APK"
    Write-Host "[PASS] Public APK audit clean: $pkg v$vn ($vc)" -ForegroundColor Green
}

function Assert-PrivateDevApk([string]$ApkPath, [string]$ExpectedVerName, [int]$ExpectedVerCode) {
    if (-not (Test-Path $ApkPath)) { throw "Missing PrivateDev APK: $ApkPath" }
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
    Assert-CommonPrivacyManifest -ManifestText $man -Label "PrivateDev APK"

    $forbidden = @(
        "BetaUpdates", "duit_qa_crash", "duit_qa_analytics", ("github_pat" + "_11"), ("ghp" + "_")
    )
    Scan-ApkMarkers -ApkPath $ApkPath -ForbiddenMarkers $forbidden -RequiredDexMarkers @("Developer Center", "duit-dev-install") -Label "PrivateDev APK"
    Write-Host "[PASS] PrivateDev APK audit clean: $pkg v$vn ($vc)" -ForegroundColor Green
}

function Sign-Or-Stage([string]$SourceUnsignedApk, [string]$DestDir, [string]$BaseFileName, [string]$PublishHint) {
    New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
    $stagedUnsigned = Join-Path $DestDir "$BaseFileName-unsigned.apk"
    $stagedSigned = Join-Path $DestDir "$BaseFileName.apk"
    Copy-Item $SourceUnsignedApk $stagedUnsigned -Force
    if ($DryRun) {
        $hash = (Get-FileHash $stagedUnsigned -Algorithm SHA256).Hash
        $size = (Get-Item $stagedUnsigned).Length
        Write-Host "[DRY-RUN PASS] Staged unsigned APK: $stagedUnsigned ($size bytes, SHA256=$hash)" -ForegroundColor Cyan
        Write-Host "[DRY-RUN] Manual sign/publish deferred: $PublishHint" -ForegroundColor Cyan
    } else {
        if (-not (Test-Path $keystore)) { throw "Original keystore missing at $keystore" }
        $tmpSigned = Join-Path $env:TEMP ("duit-sign-" + [guid]::NewGuid().ToString("N") + ".apk")
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
        $size = (Get-Item $stagedSigned).Length
        Write-Host "[SIGNED PASS] $stagedSigned ($size bytes) | SHA256=$sha" -ForegroundColor Green
        Write-Host "Next (manual approval): $PublishHint" -ForegroundColor Yellow
    }
}

Assert-BranchSynchronized
Assert-NoPublicPrivateLeak

$mappingProp = if ($UploadCrashlyticsMapping -and -not $DryRun) { "true" } else { "false" }
$runStable = ($Target -eq "Stable" -or $Target -eq "Synchronized" -or $Target -eq "All")
$runBeta = ($Target -eq "Beta" -or $Target -eq "All")
$runPrivateDev = ($Target -eq "PrivateDev" -or $Target -eq "Synchronized" -or $Target -eq "All")

if ($runStable) {
    $stableCandidate = if ($Target -in @("Stable", "Synchronized") -and $PSBoundParameters.ContainsKey("VersionName")) {
        $VersionName
    } else {
        "1.3.0"
    }
    if ($stableCandidate -notmatch "^(\d+)\.(\d+)\.(\d+)$") {
        throw "SAFETY BLOCK: Stable target requires X.Y.Z versionName (no -dev or -beta suffix), refusing: $stableCandidate"
    }
    $parts = $stableCandidate.Split(".")
    $stableCode = [int]$parts[0] * 1000000 + [int]$parts[1] * 1000 + [int]$parts[2]
    Invoke-GradleTarget @("testBetaUnitTest", "assembleBeta", "-PduitBetaVersionName=$stableCandidate", "-PduitBetaVersionCode=$stableCode", "-PduitBetaAppName=duit.", "-PduitUploadCrashlyticsMapping=$mappingProp")
    $unsignedPublic = Join-Path $repoRoot "app\build\outputs\apk\beta\app-beta-unsigned.apk"
    Assert-PublicApk -ApkPath $unsignedPublic -ExpectedVerName $stableCandidate -ExpectedVerCode $stableCode
    $publicStageDir = "C:\Users\SV\Documents\duit-release-staging\release-v$stableCandidate"
    Sign-Or-Stage -SourceUnsignedApk $unsignedPublic -DestDir $publicStageDir -BaseFileName "duit-$stableCandidate" `
        -PublishHint "gh release create v$stableCandidate `"$publicStageDir\duit-$stableCandidate.apk`" -R erlingdiniy/duit-beta --title `"duit. v$stableCandidate`" --notes-file `"$publicStageDir\RELEASE_NOTES_v$stableCandidate.md`""
}

if ($runBeta) {
    $betaVer = if ($Target -eq "Beta" -and $PSBoundParameters.ContainsKey("VersionName")) { $VersionName } else { "1.3.0-beta.1" }
    if ($betaVer -notmatch "^(\d+)\.(\d+)\.0-beta\.([1-9]\d?)$") {
        throw "SAFETY BLOCK: Beta target requires X.Y.0-beta.N versionName, refusing: $betaVer"
    }
    $betaCode = [int]$Matches[1] * 1000000 + [int]$Matches[2] * 1000 - 100 + [int]$Matches[3]
    Invoke-GradleTarget @("testBetaUnitTest", "assembleBeta", "-PduitBetaVersionName=$betaVer", "-PduitBetaVersionCode=$betaCode", "-PduitBetaAppName=duit. Beta", "-PduitUploadCrashlyticsMapping=$mappingProp")
    $unsignedBeta = Join-Path $repoRoot "app\build\outputs\apk\beta\app-beta-unsigned.apk"
    Assert-PublicApk -ApkPath $unsignedBeta -ExpectedVerName $betaVer -ExpectedVerCode $betaCode
}

if ($runPrivateDev) {
    $devVer = if ($VersionName -match "^(\d+)\.(\d+)\.(\d+)-dev(?:\.([1-9]\d?))?$") {
        $VersionName
    } elseif ($VersionName -match "^(\d+)\.(\d+)\.(\d+)$") {
        "$VersionName-dev.$DevPatch"
    } else {
        throw "PrivateDev requires X.Y.Z or X.Y.Z-dev[.N], got $VersionName"
    }
    if ($devVer -notmatch "^(\d+)\.(\d+)\.(\d+)-dev(?:\.([1-9]\d?))?$") {
        throw "Invalid PrivateDev versionName: $devVer"
    }
    $baseMajor = [int]$Matches[1]
    $baseMinor = [int]$Matches[2]
    $basePatch = [int]$Matches[3]
    $devPatchNum = if ($Matches[4]) { [int]$Matches[4] } else { 0 }
    $baseStableVer = "$baseMajor.$baseMinor.$basePatch"
    $baseStableCode = $baseMajor * 1000000 + $baseMinor * 1000 + $basePatch
    $expectedDevCode = $baseMajor * 10000000 + $baseMinor * 10000 + $basePatch * 100 + $devPatchNum
    if ($VersionCode -gt 0 -and $VersionCode -ne $expectedDevCode) {
        throw "VersionCode $VersionCode does not match required formula $expectedDevCode"
    }

    Invoke-GradleTarget @(
        "testBetaDevUnitTest",
        "assembleBetaDev",
        "-PduitBetaVersionName=$baseStableVer",
        "-PduitBetaVersionCode=$baseStableCode",
        "-PduitBetaAppName=duit.",
        "-PduitBetaDevVersionName=$devVer",
        "-PduitBetaDevVersionCode=$expectedDevCode",
        "-PduitUploadCrashlyticsMapping=$mappingProp"
    )

    $unsignedDev = Join-Path $repoRoot "app\build\outputs\apk\betaDev\app-betaDev-unsigned.apk"
    Assert-PrivateDevApk -ApkPath $unsignedDev -ExpectedVerName $devVer -ExpectedVerCode $expectedDevCode
    $devStageDir = Join-Path "C:\Users\SV\Documents\duit-release-staging\private-dev" "v$devVer"
    Sign-Or-Stage -SourceUnsignedApk $unsignedDev -DestDir $devStageDir -BaseFileName "duit-$devVer" `
        -PublishHint "gh release create v$devVer `"$devStageDir\duit-$devVer.apk`" -R erlingdiniy/duit-developer-console --title `"duit. v$devVer (Private Developer)`" --notes-file `"$devStageDir\RELEASE_NOTES_v$devVer.md`""
}

Assert-NoPublicPrivateLeak
