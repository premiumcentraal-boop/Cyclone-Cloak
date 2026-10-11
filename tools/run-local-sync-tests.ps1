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
$jar="$env:TEMP/cloak-sync-publication-tests.jar"
# Fail visibly if real process-lock tests cannot run; never count skipped shell cases as verification.
& wsl.exe -d Ubuntu --exec /bin/sh -c 'command -v flock' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Ubuntu WSL with flock is required for publication checks.' }
$env:CLOAK_TEST_WSL="1"
$sources=@('android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt','android/app/src/main/java/dev/cyclone/cloak/root/CloakResolver.kt','android/app/src/main/java/dev/cyclone/cloak/ui/HealthDiagnostics.kt','android/app/src/test/java/dev/cyclone/cloak/root/PublicationRecoveryTest.kt','android/app/src/test/java/dev/cyclone/cloak/root/CloakPublishMergeTest.kt','android/app/src/test/java/dev/cyclone/cloak/root/CloakRootDoctorTest.kt','android/app/src/test/java/dev/cyclone/cloak/ui/HealthDiagnosticsTest.kt')
$sources+=@('android/app/src/main/java/dev/cyclone/cloak/data/CloakBindingStore.kt','android/app/src/main/java/dev/cyclone/cloak/cyclone/Reconcile.kt','android/app/src/main/java/dev/cyclone/cloak/cyclone/SyncEngine.kt','android/app/src/test/java/dev/cyclone/cloak/cyclone/FakeCyclone.kt','android/app/src/test/java/dev/cyclone/cloak/forge/TestPhones.kt','android/app/src/test/java/dev/cyclone/cloak/cyclone/ReconciliationBehaviorTest.kt','android/app/src/test/java/dev/cyclone/cloak/cyclone/CycloneSyncTest.kt','android/app/src/test/java/dev/cyclone/cloak/cyclone/CycloneVectorsTest.kt','android/app/src/test/java/dev/cyclone/cloak/data/CloakBindingStoreTest.kt')
& $java -cp "$KotlinHome/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler @sources -classpath $cp -include-runtime -d $jar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java -cp "$jar;$cp;catalog;android/app/src/test/resources" org.junit.runner.JUnitCore dev.cyclone.cloak.root.PublicationRecoveryTest dev.cyclone.cloak.root.CloakPublishMergeTest dev.cyclone.cloak.root.CloakRootDoctorTest dev.cyclone.cloak.ui.HealthDiagnosticsTest dev.cyclone.cloak.cyclone.ReconciliationBehaviorTest dev.cyclone.cloak.cyclone.CycloneSyncTest dev.cyclone.cloak.cyclone.CycloneVectorsTest dev.cyclone.cloak.data.CloakBindingStoreTest
exit $LASTEXITCODE
