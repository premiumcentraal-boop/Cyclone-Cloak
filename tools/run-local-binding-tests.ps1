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
$jar="$env:TEMP/cloak-binding-flow-tests.jar"
$sources=@('android/app/src/main/java/dev/cyclone/cloak/data/CloakBindingStore.kt','android/app/src/main/java/dev/cyclone/cloak/ui/BindingFlow.kt','android/app/src/test/java/dev/cyclone/cloak/ui/BindingFlowTest.kt','android/app/src/test/java/dev/cyclone/cloak/cyclone/BulkBindTest.kt','android/app/src/test/java/dev/cyclone/cloak/forge/TestPhones.kt')
& $java -cp "$KotlinHome/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler @sources -classpath $cp -include-runtime -d $jar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java -cp "$jar;$cp;catalog" org.junit.runner.JUnitCore dev.cyclone.cloak.ui.BindingFlowTest dev.cyclone.cloak.cyclone.BulkBindTest
exit $LASTEXITCODE
