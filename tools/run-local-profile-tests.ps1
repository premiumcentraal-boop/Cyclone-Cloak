# Supplemental JVM checks; does not replace the Gradle/Robolectric test target.
param(
    [string]$JdkHome = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot',
    [string]$KotlinHome = 'C:/Users/Agent/kotlinc-home/kotlinc',
    [string]$AndroidSdk = 'C:/Users/Agent/android-sdk'
)
Set-Location (Split-Path $PSScriptRoot -Parent)
$ErrorActionPreference='Stop'
$java="$JdkHome/bin/java.exe"
$json=(Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/org.json/json/20240303" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$junit=(Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$hamcrest=(Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$cp="$json;$junit;$hamcrest;$PWD/android/app/build/tmp/kotlin-classes/debug;$AndroidSdk/platforms/android-36/android.jar"
$jar="$env:TEMP/cloak-generation-tests.jar"
$sources=@(Get-ChildItem android/app/src/main/java/dev/cyclone/cloak/forge -Filter '*.kt' | ForEach-Object FullName)
$sources+=@(Get-ChildItem android/app/src/test/java/dev/cyclone/cloak/forge -Filter '*.kt' | ForEach-Object FullName)
$sources+=@('android/app/src/main/java/dev/cyclone/cloak/ui/ProfileGeneration.kt','android/app/src/main/java/dev/cyclone/cloak/data/JsonDirectory.kt','android/app/src/test/java/dev/cyclone/cloak/ui/ProfileGenerationTest.kt','android/app/src/main/java/dev/cyclone/cloak/ui/HealthDiagnostics.kt','android/app/src/test/java/dev/cyclone/cloak/ui/HealthDiagnosticsTest.kt','android/app/src/main/java/dev/cyclone/cloak/ui/ReadinessDoctorFacts.kt','android/app/src/test/java/dev/cyclone/cloak/ui/ReadinessDoctorFactsTest.kt')
& $java -cp "$KotlinHome/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler @sources -classpath $cp -include-runtime -d $jar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$tests=@(Get-ChildItem android/app/src/test/java/dev/cyclone/cloak/forge -Filter '*Test.kt' | ForEach-Object { 'dev.cyclone.cloak.forge.'+$_.BaseName })
$tests+=@("dev.cyclone.cloak.ui.ProfileGenerationTest", "dev.cyclone.cloak.ui.HealthDiagnosticsTest", "dev.cyclone.cloak.ui.ReadinessDoctorFactsTest")
& $java -cp "$jar;$cp;catalog;forge/tests/vectors" org.junit.runner.JUnitCore @tests
exit $LASTEXITCODE
