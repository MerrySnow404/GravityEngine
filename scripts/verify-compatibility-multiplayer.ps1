param([int]$Port=25576, [int]$TimeoutSeconds=480, [ValidateRange(0,1000)][int]$DelayMillis=100, [switch]$ConcurrentAssignment, [switch]$TraceGravity, [switch]$MovingPlatforms, [switch]$ChildPlatforms, [switch]$TraceMovement)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$target=Join-Path $repo 'targets/neoforge-1.21.1'
$prepared=Join-Path $target 'build/moddev'
$launch=Get-Content -LiteralPath (Join-Path $prepared 'multiplayer-launch.json') -Raw | ConvertFrom-Json
$serverDirectory=Join-Path $target 'run/sable-verification'
if (!(Select-String -LiteralPath (Join-Path $serverDirectory 'eula.txt') -Pattern '^eula=true$' -Quiet)) {
    throw 'Accept the Minecraft EULA in the existing verification directory before running this check.'
}
$evidence=Join-Path $repo ('build/multiplayer/'+$launch.profile+'/'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$processes=@()
$previousMods=$env:MOD_CLASSES
$propertiesPath=Join-Path $serverDirectory 'server.properties'
$originalProperties=[IO.File]::ReadAllText($propertiesPath)
[IO.File]::WriteAllText((Join-Path $evidence 'original-server.properties'),$originalProperties)
function Write-LaunchArgs([string]$name,[string]$run,[string]$extra) {
    $vm=Get-Content -LiteralPath (Join-Path $prepared ($run+'RunVmArgs.txt')) -Raw
    $program=Get-Content -LiteralPath (Join-Path $prepared ($run+'RunProgramArgs.txt')) -Raw
    if($name -eq 'server') {
        $program=$program -replace '(?m)^0\r?$', [string]$Port
        # A fresh multiplayer world must not inherit the server-control fixtures.
        $world='multiplayer-'+$launch.profile+'-'+(Split-Path $evidence -Leaf)
        $worldArgument='(?m)(^--world\r?\n)[^\r\n]+'
        if([regex]::Matches($program,$worldArgument).Count -ne 1) { throw 'Prepared server must name exactly one world.' }
        $program=[regex]::Replace($program,$worldArgument,('${1}'+$world))
    }
    $argsFile=Join-Path $evidence ($name+'-args.txt')
    $settings="`n-Xmx2G`n-Dgravityengine.multiplayerVerification=true`n-Dgravityengine.multiplayerPort=$Port`n"
    $settings+="-Dgravityengine.multiplayerDelayMillis=$DelayMillis`n"
    if($ConcurrentAssignment) { $settings+="-Dgravityengine.multiplayerConcurrentAssignment=true`n" }
    if($MovingPlatforms -or $ChildPlatforms) { $settings+="-Dgravityengine.multiplayerPlatforms=true`n" }
    if($ChildPlatforms) { $settings+="-Dgravityengine.multiplayerChildPlatforms=true`n" }
    if($TraceGravity) { $settings+="-Dgravityengine.debugGravity=MUTATION`n-Dgravityengine.debugSpatialState=false`n" }
    if($TraceMovement) {
        $traceId=if($name -eq 'GESecond') {'0d8eef00-4e2b-32c7-85f1-024dd3f7e0c3'} else {'ea8f29c3-70ff-3532-8bbf-869959d3a2a8'}
        $settings+="-Dgravityengine.traceRigidUnknown=true`n-Dgravityengine.debugMovement=FULL`n-Dgravityengine.debugSpatialState=false`n-Dgravityengine.debugEntity=$traceId`n-Dgravityengine.debugSide=CLIENT`n"
    }
    $settings+='"-Dgravityengine.multiplayerEvidence='+$evidence.Replace('\','/')+'"'+"`n"
    foreach($argument in $launch.additionalVmArgs) { $settings+='"'+$argument.Replace('\','/').Replace('"','\"')+'"'+"`n" }
    $classpath=if($name -eq 'server') {$launch.serverClasspath} else {$launch.clientClasspath}
    $settings+="-cp`n"+'"'+$classpath.Replace('\','/')+'"'+"`n"
    [IO.File]::WriteAllText($argsFile,$vm+$settings+$program+"`n"+$extra,[Text.UTF8Encoding]::new($false))
    return $argsFile
}
try {
    [IO.File]::WriteAllText($propertiesPath,($originalProperties -replace '(?m)^server-ip=.*$', 'server-ip=127.0.0.1'))
    if($launch.modClasses) { $env:MOD_CLASSES=$launch.modClasses }
    $sourceSha=git -C $repo rev-parse HEAD
    if($LASTEXITCODE) { throw 'Cannot record multiplayer source SHA.' }
    $patch=Join-Path $evidence 'working-tree.patch'
    git -C $repo diff --binary --no-ext-diff "--output=$patch"
    if($LASTEXITCODE) { throw 'Cannot record multiplayer source diff.' }
    $sourcePaths=git -C $repo ls-files --cached --others --exclude-standard
    $sourceHashes=@($sourcePaths | Sort-Object -Unique | ForEach-Object {
        $source=Join-Path $repo $_
        if(Test-Path -LiteralPath $source -PathType Leaf) {
            @{path=$_;sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash}
        }
    })
    foreach($addition in (git -C $repo ls-files --others --exclude-standard)) {
        $destination=Join-Path $evidence ('source-additions/'+$addition)
        New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $repo $addition) -Destination $destination
    }
    $runtimeJars=@((Join-Path $target 'build/libs/GravityEngine-neoforge-1.21.1-0.0.2.jar'))
    $runtimeJars+=@(($launch.serverClasspath+';'+$launch.clientClasspath) -split ';' | Where-Object {
        $_ -match '(sable|create-|aeronautics|registrate|flywheel|ponder|veil|neoforge-21\.1\.256)' -and $_.EndsWith('.jar')
    })
    $artifacts=@($runtimeJars | Sort-Object -Unique | ForEach-Object {
        @{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}
    })
    @{
        startedUtc=[datetime]::UtcNow.ToString('o');sourceSha=$sourceSha;profile=$launch.profile
        java=$launch.java;delayMillis=$DelayMillis;concurrentAssignment=[bool]$ConcurrentAssignment
        movingPlatforms=[bool]($MovingPlatforms -or $ChildPlatforms);childPlatforms=[bool]$ChildPlatforms
        traceMovement=[bool]$TraceMovement;traceGravity=[bool]$TraceGravity;port=$Port
        scope='Ordered inbound TCP and Sable UDP delay; no packet loss or reordering.'
        sourceFiles=$sourceHashes;artifacts=$artifacts
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidence 'manifest.json') -Encoding UTF8
    foreach($name in @('server','GEFirst','GESecond')) {
        $run=if($name -eq 'server') {'sableVerification'} else {'compatibilityClient'}
        $directory=if($name -eq 'server') {$serverDirectory} else {Join-Path $evidence $name}
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        $extra=if($name -eq 'server') {''} else {"--username`n$name`n--width`n640`n--height`n480"}
        $argsFile=Write-LaunchArgs $name $run $extra
        $processes+=Start-Process -FilePath $launch.java -ArgumentList ('@"'+$argsFile+'"') -WorkingDirectory $directory `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence ($name+'.log')) `
            -RedirectStandardError (Join-Path $evidence ($name+'-stderr.log'))
    }
    Write-Output "MULTIPLAYER_EVIDENCE=$evidence"
    $deadline=(Get-Date).AddSeconds($TimeoutSeconds)
    while((Get-Date) -lt $deadline) {
        foreach($clientName in @('GEFirst','GESecond')) {
            $crashDirectory=Join-Path $evidence ($clientName+'/crash-reports')
            if(Test-Path -LiteralPath $crashDirectory) {
                $crashes=@(Get-ChildItem -LiteralPath $crashDirectory -Filter '*.txt')
                if($crashes.Count) { throw "Fresh client crash: $($crashes[0].FullName)" }
            }
        }
        $failed=@(Get-ChildItem -LiteralPath $evidence -Filter '*-FAIL.txt')
        if($failed.Count) { throw (Get-Content -LiteralPath $failed[0].FullName -Raw) }
        if((Test-Path -LiteralPath (Join-Path $evidence 'server-PASS.txt')) -and
           (Test-Path -LiteralPath (Join-Path $evidence 'GEFirst-PASS.txt')) -and
           (Test-Path -LiteralPath (Join-Path $evidence 'GESecond-PASS.txt'))) {
            Write-Output 'PASS dedicated server and both real clients'; return
        }
        foreach($process in $processes) {
            if($process.HasExited -and !(Test-Path -LiteralPath (Join-Path $evidence 'server-PASS.txt'))) {
                throw "Verification JVM $($process.Id) exited $($process.ExitCode) before server acceptance"
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'Multiplayer verification timed out; missing fresh PASS is a failure.'
} finally {
    # Only processes started by this invocation are eligible for termination.
    foreach($process in $processes) { if(!$process.HasExited) { Stop-Process -InputObject $process -Force } }
    $env:MOD_CLASSES=$previousMods
    [IO.File]::WriteAllText($propertiesPath,$originalProperties)
}
