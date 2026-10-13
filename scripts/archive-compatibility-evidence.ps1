param(
    [Parameter(Mandatory)][ValidateSet('sable','create','aeronautics')][string]$Profile,
    [Parameter(Mandatory)][string]$Log,
    [Parameter(Mandatory)][datetime]$StartedUtc,
    [Parameter(Mandatory)][string]$TaskCommand
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$target=Join-Path $repo 'targets/neoforge-1.21.1'
$output=Join-Path $repo ('build/compatibility-evidence/'+$Profile+'/'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
$launch=Get-Content -LiteralPath (Join-Path $target 'build/moddev/multiplayer-launch.json') -Raw | ConvertFrom-Json
if($launch.profile -ne $Profile) { throw 'Prepare launch metadata for the profile being archived.' }
$required=@('control-boundary-result.txt','sable-checks-result.txt')
if($Profile -ne 'sable') { $required+='create-checks-result.txt' }
if($Profile -eq 'aeronautics') { $required+='aeronautics-checks-result.txt' }
$results=@($required | ForEach-Object { Join-Path $target ('run/sable-verification/'+$_) })
$results+=Join-Path $target 'run/compatibility-client/control-boundary-result.txt'
foreach($result in $results) {
    $file=Get-Item -LiteralPath $result
    if($file.LastWriteTimeUtc -lt $StartedUtc.ToUniversalTime()) { throw "Stale result: $result" }
    if(!(Get-Content -LiteralPath $result -First 1).StartsWith('PASS')) { throw "Non-PASS result: $result" }
}
New-Item -ItemType Directory -Path $output -Force | Out-Null
Copy-Item -LiteralPath $Log -Destination (Join-Path $output 'stdout.log')
foreach($result in $results) {
    $side=if($result.Contains('compatibility-client')) {'client'} else {'server'}
    Copy-Item -LiteralPath $result -Destination (Join-Path $output ($side+'-'+(Split-Path $result -Leaf)))
}
$sourceSha=git -C $repo rev-parse HEAD
if($LASTEXITCODE) { throw 'Cannot record source SHA.' }
$patch=Join-Path $output 'working-tree.patch'
git -C $repo diff --binary --no-ext-diff "--output=$patch"
if($LASTEXITCODE) { throw 'Cannot record tracked source diff.' }
$sourcePaths=git -C $repo ls-files --cached --others --exclude-standard
$additions=git -C $repo ls-files --others --exclude-standard
foreach($addition in $additions) {
    if($addition -notmatch '^(common|targets|scripts)/') { continue }
    $destination=Join-Path $output ('source-additions/'+$addition)
    New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo $addition) -Destination $destination
}
$sourceHashes=@($sourcePaths | Sort-Object -Unique | ForEach-Object {
    $source=Join-Path $repo $_
    if(Test-Path -LiteralPath $source -PathType Leaf) {
        @{path=$_;sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash}
    }
})
$jars=@((Join-Path $target 'build/libs/GravityEngine-neoforge-1.21.1-0.0.2.jar'))
$jars+=@($launch.serverClasspath -split ';' | Where-Object {
    $_ -match '(sable|create-|aeronautics|registrate|flywheel|ponder|veil|neoforge-21\.1\.256)' -and $_.EndsWith('.jar')
})
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifacts=@($jars | Sort-Object -Unique | ForEach-Object {
    $jar=$_
    $nested=@()
    $archive=[IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach($entry in $archive.Entries) {
            if(!$entry.FullName.StartsWith('META-INF/jarjar/') -or !$entry.FullName.EndsWith('.jar')) { continue }
            $stream=$entry.Open();$digest=[Security.Cryptography.SHA256]::Create()
            try { $hash=[BitConverter]::ToString($digest.ComputeHash($stream)).Replace('-','') }
            finally { $digest.Dispose();$stream.Dispose() }
            $nested+=@{entry=$entry.FullName;sha256=$hash}
        }
    } finally { $archive.Dispose() }
    @{path=$jar;sha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash;embedded=$nested}
})
$manifest=@{
    sourceSha=$sourceSha;profile=$Profile;startedUtc=$StartedUtc.ToUniversalTime().ToString('o')
    archivedUtc=[datetime]::UtcNow.ToString('o');java=$launch.java
    command=$TaskCommand
    scope='Fresh strict server and integrated-client results; not complete compatibility acceptance.'
    sourceFiles=$sourceHashes;artifacts=$artifacts
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output 'manifest.json') -Encoding UTF8
foreach($suite in @(@{name='common-junit';path=(Join-Path $repo 'common/build/test-results')},
                    @{name='target-junit';path=(Join-Path $target 'build/test-results')})) {
    if(Test-Path -LiteralPath $suite.path) {
        Copy-Item -LiteralPath $suite.path -Destination (Join-Path $output $suite.name) -Recurse
    }
}
Write-Output "EVIDENCE=$output"
