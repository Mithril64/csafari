# Generates the real Gradle wrapper (including gradle-wrapper.jar) without
# requiring Gradle to be installed. Run once, then use .\gradlew.bat as normal.
#
#   powershell -ExecutionPolicy Bypass -File bootstrap.ps1

$ErrorActionPreference = "Stop"

$GradleVersion = "9.4.1"
$DistUrl   = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
$CacheDir  = Join-Path $env:USERPROFILE ".gradle-bootstrap"
$GradleDir = Join-Path $CacheDir "gradle-$GradleVersion"
$GradleBat = Join-Path $GradleDir "bin\gradle.bat"

Set-Location $PSScriptRoot

if (-not (Test-Path $GradleBat)) {
    Write-Host "Downloading Gradle $GradleVersion..."
    New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null
    $Zip = Join-Path $CacheDir "gradle-$GradleVersion-bin.zip"
    Invoke-WebRequest -Uri $DistUrl -OutFile $Zip
    Expand-Archive -Path $Zip -DestinationPath $CacheDir -Force
    Remove-Item $Zip
}

Write-Host "Generating wrapper..."
& $GradleBat wrapper --gradle-version $GradleVersion

Write-Host ""
Write-Host "Done. Now run:  .\gradlew.bat build"
Write-Host "The jar will be in build\libs\"
