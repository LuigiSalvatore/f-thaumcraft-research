<#
.SYNOPSIS
    Builds and publishes a GitHub release with the reobfuscated jar attached.

.DESCRIPTION
    Requires GitHub CLI (gh) to be installed and authenticated.
    Install: winget install GitHub.cli   (then: gh auth login)

    Usage:
        .\scripts\release.ps1 -Version "v1.2.0"
        .\scripts\release.ps1 -Version "v1.2.0" -Notes "Bugfixes and improvements"
        .\scripts\release.ps1 -Version "v1.2.0" -Draft

.PARAMETER Version
    The release tag / version string (e.g. "v1.2.0"). Must start with "v".

.PARAMETER Notes
    Optional release notes body. Defaults to an auto-generated summary from recent git log.

.PARAMETER Draft
    If set, creates the release as a draft instead of publishing it immediately.
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^v\d+\.\d+')]
    [string]$Version,

    [string]$Notes = "",

    [switch]$Draft
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---- Prerequisites ----------------------------------------------------------

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host @"
ERROR: GitHub CLI (gh) is not installed or not on PATH.

Install it with:
    winget install GitHub.cli

Then authenticate:
    gh auth login

See: https://cli.github.com
"@ -ForegroundColor Red
    exit 1
}

gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: GitHub CLI is not authenticated. Run: gh auth login" -ForegroundColor Red
    exit 1
}

# ---- Repository root --------------------------------------------------------

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot

try {

    # ---- Check working tree -------------------------------------------------

    # Only warn about tracked-file changes (staged or modified), not untracked files (??)
    $gitStatus = git status --porcelain 2>&1 | Where-Object { $_ -notmatch '^\?\?' }
    if ($gitStatus) {
        Write-Warning "Working tree has uncommitted changes:"
        $gitStatus | ForEach-Object { Write-Warning "  $_" }
        $confirm = Read-Host "Release anyway? (y/N)"
        if ($confirm -notmatch '^[yY]') {
            Write-Host "Aborted." -ForegroundColor Yellow
            exit 0
        }
    }

    # ---- Tag collision check ------------------------------------------------

    $existingTag = git tag --list $Version 2>&1
    if ($existingTag) {
        Write-Host "ERROR: Tag '$Version' already exists. Delete it first: git tag -d $Version" -ForegroundColor Red
        exit 1
    }

    # ---- Build --------------------------------------------------------------

    Write-Host "Building $Version ..." -ForegroundColor Cyan
    .\gradlew build --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Build FAILED -- aborting. No tag was created." -ForegroundColor Red
        exit 1
    }
    Write-Host "Build succeeded." -ForegroundColor Green

    # ---- Find the reobfuscated jar ------------------------------------------

    # The GTNH buildscript names jars with git metadata; we want the reobf jar
    # (no -dev, no -sources). The freshest one wins.
    $jar = Get-ChildItem "$repoRoot\build\libs\*.jar" |
        Where-Object { $_.Name -notmatch '-dev\.jar$' -and $_.Name -notmatch '-sources\.jar$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jar) {
        Write-Host "ERROR: No reobfuscated jar found in build/libs/. Aborting." -ForegroundColor Red
        exit 1
    }
    Write-Host "Jar: $($jar.Name)" -ForegroundColor Cyan

    # ---- Tag and push -------------------------------------------------------

    Write-Host "Tagging $Version ..." -ForegroundColor Cyan
    git tag $Version
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: git tag failed. Aborting." -ForegroundColor Red
        exit 1
    }

    git push origin $Version
    if ($LASTEXITCODE -ne 0) {
        git tag -d $Version
        Write-Host "ERROR: git push of tag failed. Local tag removed. Check remote access and retry." -ForegroundColor Red
        exit 1
    }
    Write-Host "Tag pushed." -ForegroundColor Green

    # ---- Auto-generate notes if none provided --------------------------------

    if (-not $Notes) {
        $prevTag = git describe --tags --abbrev=0 "HEAD^" 2>&1
        if ($LASTEXITCODE -eq 0 -and $prevTag) {
            $logLines = git log --oneline "${prevTag}..HEAD" 2>&1 | Out-String
            $Notes = "## Changes since $prevTag`n`n" + $logLines.Trim()
        } else {
            $Notes = "Initial release."
        }
    }

    # ---- Create GitHub release ----------------------------------------------

    $title = "Thaumcraft Research Solver $Version"

    $ghArgs = @(
        "release", "create", $Version,
        "--title", $title,
        "--notes", $Notes,
        $jar.FullName
    )

    if ($Draft) {
        $ghArgs += "--draft"
    }

    Write-Host "Creating GitHub release '$title' ..." -ForegroundColor Cyan
    gh @ghArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: gh release create failed. Tag already pushed; delete manually if needed: git push origin --delete $Version" -ForegroundColor Red
        exit 1
    }

    Write-Host ""
    Write-Host "Release $Version published successfully!" -ForegroundColor Green

} finally {
    Pop-Location
}
