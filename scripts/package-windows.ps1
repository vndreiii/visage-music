# Build the Windows artifacts into dist\: portable .zip, installer .msi and setup .exe.
# Usage: $env:VISAGE_VERSION = "1.2.3"; .\scripts\package-windows.ps1
# Needs: JDK 21, Rust, WiX Toolset 3 (jpackage uses it), 7-Zip and the GitHub CLI.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$version = if ($env:VISAGE_VERSION) { $env:VISAGE_VERSION.TrimStart("v") } else { "1.0.0" }
# Windows installers need MAJOR.MINOR.PATCH numbers only.
$pkgVersion = ($version -split "-")[0]
if ($pkgVersion -notmatch '^\d+\.\d+\.\d+$') { $pkgVersion = "1.0.0" }
$env:VISAGE_VERSION = $pkgVersion

$deps = Join-Path $root "composeApp\build\windows-deps"
New-Item -ItemType Directory -Force $deps, "dist" | Out-Null
Remove-Item "dist\*" -Include *.zip, *.msi, *.exe -ErrorAction SilentlyContinue

# 1. Rust helper (library scan, tags, fuzzy search).
cargo build --release --manifest-path native/Cargo.toml
if ($LASTEXITCODE -ne 0) { throw "cargo build failed" }

# 2. libmpv: the playback engine. Not on Windows by default, so it ships inside the app.
if (-not (Test-Path "$deps\libmpv-2.dll")) {
    $tmp = Join-Path $env:TEMP "visage-mpv"
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory $tmp | Out-Null
    gh release download --repo shinchiro/mpv-winbuild-cmake --pattern "mpv-dev-x86_64-2*.7z" --dir $tmp
    if ($LASTEXITCODE -ne 0) { throw "libmpv download failed" }
    7z x (Get-ChildItem "$tmp\*.7z" | Select-Object -First 1).FullName "-o$tmp\x" -y | Out-Null
    $dll = Get-ChildItem "$tmp\x" -Recurse -Filter "libmpv-2.dll" | Select-Object -First 1
    if (-not $dll) { throw "libmpv-2.dll not found in the archive" }
    Copy-Item $dll.FullName $deps
}

# 3. yt-dlp: stream fallback and offline downloads. The checksum comes from the same release.
if (-not (Test-Path "$deps\yt-dlp.exe")) {
    $base = "https://github.com/yt-dlp/yt-dlp/releases/latest/download"
    Invoke-WebRequest "$base/yt-dlp.exe" -OutFile "$deps\yt-dlp.exe"
    $sums = (Invoke-WebRequest "$base/SHA2-256SUMS").Content
    $want = ($sums -split "`n" | Where-Object { $_ -match '\s\*?yt-dlp\.exe$' } | Select-Object -First 1) -split '\s+' | Select-Object -First 1
    $have = (Get-FileHash "$deps\yt-dlp.exe" -Algorithm SHA256).Hash.ToLower()
    if ($want -ne $have) { Remove-Item "$deps\yt-dlp.exe"; throw "yt-dlp checksum mismatch" }
}

# 4. App image, installers.
.\gradlew.bat --no-daemon :composeApp:createDistributable :composeApp:packageMsi :composeApp:packageExe
if ($LASTEXITCODE -ne 0) { throw "gradle failed" }

$bin = "composeApp\build\compose\binaries\main"
$app = Get-ChildItem "$bin\app" -Directory | Select-Object -First 1
# The marker tells the updater this copy is portable: it opens the release page instead of installing.
New-Item -ItemType File -Force (Join-Path $app.FullName "portable") | Out-Null
$stage = Join-Path $root "build\portable-stage"
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $stage | Out-Null
Copy-Item $app.FullName (Join-Path $stage "VIsage") -Recurse
Compress-Archive -Path "$stage\VIsage" -DestinationPath "dist\VIsage-$version-windows-x64-portable.zip" -Force

# The stable name VIsage-x64.msi is what the in-app updater looks for.
Copy-Item (Get-ChildItem "$bin\msi\*.msi" | Select-Object -First 1).FullName "dist\VIsage-x64.msi"
Copy-Item (Get-ChildItem "$bin\exe\*.exe" | Select-Object -First 1).FullName "dist\VIsage-$version-windows-x64-setup.exe"
Get-ChildItem dist | Format-Table Name, Length
