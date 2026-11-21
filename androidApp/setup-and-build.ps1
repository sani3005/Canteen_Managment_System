$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
if (-not $env:JAVA_HOME) { Write-Host "JAVA_HOME not set; Gradle will use Java from PATH." }
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
 Write-Host "Set ANDROID_HOME to your SDK directory if the build cannot find Android SDK." -ForegroundColor Yellow
}
$gradleVersion = "8.13"
$zip = Join-Path $root ".bootstrap-gradle.zip"
$folder = Join-Path $root ".bootstrap-gradle"
$exe = Join-Path $folder "gradle-$gradleVersionin\gradle.bat"
if (-not (Test-Path $exe)) {
 Write-Host "Downloading Gradle $gradleVersion (internet required for first build)..."
 Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $zip
 Expand-Archive -Path $zip -DestinationPath $folder -Force
 Remove-Item $zip
}
& $exe wrapper --gradle-version $gradleVersion --distribution-type bin
if ($LASTEXITCODE -ne 0) { throw "Could not generate Gradle wrapper" }
& (Join-Path $root "gradlew.bat") --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
Write-Host "APK: $rootppuild\outputspk\debugpp-debug.apk" -ForegroundColor Green
