# push-via-api.ps1 —— git 传输被阻断时用 GitHub Git Data API 回放提交历史
# 用法:
#   tools/push-via-api.ps1 -Repo material-mail-full                 # 全量
#   tools/push-via-api.ps1 -Repo material-mail -ExcludePaths pro    # 社区版（剔除 pro/）
# 原理: blob/tree/commit 内容寻址 SHA-1，全量回放时远端 SHA 与本地一致。
# 使用 -ExcludePaths 时 SHA 必然不同（历史被净化），仅触及被排除路径的提交被跳过、
# 父子链自动重接。
param(
    [Parameter(Mandatory = $true)][string]$Repo,
    [string]$Owner = 'Tangmjiu',
    [string]$Branch = 'main',
    [string[]]$ExcludePaths = @()
)
$ErrorActionPreference = 'Stop'
$tmpDir = Join-Path $env:TEMP ('gh-api-push-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

function Test-Excluded($path) {
    foreach ($ex in $ExcludePaths) {
        if ($path -eq $ex -or $path.StartsWith($ex.TrimEnd('/') + '/')) { return $true }
    }
    return $false
}

function Invoke-GhApi($method, $path, $bodyObject) {
    $jsonFile = Join-Path $tmpDir 'body.json'
    [System.IO.File]::WriteAllText($jsonFile, ($bodyObject | ConvertTo-Json -Depth 10 -Compress), [System.Text.UTF8Encoding]::new($false))
    $out = gh api -X $method $path --input $jsonFile 2>&1
    if ($LASTEXITCODE -ne 0) { throw "gh api $method $path 失败: $out" }
    return ($out | ConvertFrom-Json)
}

Write-Host "== 收集提交与 blob（排除: $($ExcludePaths -join ', ')) =="
$commits = git rev-list --reverse $Branch
$blobShas = [ordered]@{}
foreach ($c in $commits) {
    git ls-tree -r $c | ForEach-Object {
        $parts = $_ -split "`t", 2
        $meta = $parts[0] -split ' '
        if ($meta[1] -eq 'blob' -and -not (Test-Excluded $parts[1])) { $blobShas[$meta[2]] = $true }
    }
}
Write-Host ("提交数: {0}, 唯一 blob 数: {1}" -f $commits.Count, $blobShas.Count)

Write-Host "== 上传 blob =="
$i = 0
foreach ($sha in $blobShas.Keys) {
    $i++
    $tmpFile = Join-Path $tmpDir 'blob.bin'
    & cmd /c "git cat-file blob $sha > `"$tmpFile`""
    $b64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($tmpFile))
    Invoke-GhApi 'POST' "/repos/$Owner/$Repo/git/blobs" @{ content = $b64; encoding = 'base64' } | Out-Null
    if ($i % 25 -eq 0) { Write-Host "  $i / $($blobShas.Count)" }
}

Write-Host "== 逐提交重建 tree + commit =="
$remoteByLocal = @{}   # 本地 commit sha -> 远端 commit sha（被跳过的提交映射到其父）
foreach ($c in $commits) {
    $entries = @()
    git ls-tree -r $c | ForEach-Object {
        $parts = $_ -split "`t", 2
        $meta = $parts[0] -split ' '
        if (-not (Test-Excluded $parts[1])) {
            $entries += @{ path = $parts[1]; mode = $meta[0]; type = $meta[1]; sha = $meta[2] }
        }
    }
    $tree = Invoke-GhApi 'POST' "/repos/$Owner/$Repo/git/trees" @{ tree = $entries }

    # 父链重接：本地父提交可能已被跳过
    $localParents = git show -s --format='%P' $c
    $remoteParents = @($localParents -split ' ' | Where-Object { $_ } | ForEach-Object { $remoteByLocal[$_] } | Where-Object { $_ })

    # 只触及排除路径的提交：tree 与父一致 → 跳过
    if ($remoteParents.Count -eq 1) {
        $parentTree = gh api "repos/$Owner/$Repo/git/commits/$($remoteParents[0])" --jq '.tree.sha' 2>&1
        if ($parentTree -eq $tree.sha) {
            Write-Host ("  {0} 跳过（仅含被排除路径的变更）" -f $c.Substring(0, 7))
            $remoteByLocal[$c] = $remoteParents[0]
            continue
        }
    }

    $metaLine = git show -s --format='%an%n%ae%n%aI%n%cn%n%ce%n%cI' $c
    $m = $metaLine -split "`n"
    $message = (git log -1 --format=%B $c) -join "`n"
    $commitBody = @{
        message = $message
        tree = $tree.sha
        parents = $remoteParents
        author = @{ name = $m[0]; email = $m[1]; date = $m[2] }
        committer = @{ name = $m[3]; email = $m[4]; date = $m[5] }
    }
    $newCommit = Invoke-GhApi 'POST' "/repos/$Owner/$Repo/git/commits" $commitBody
    $mark = if ($newCommit.sha -eq $c) { 'sha一致' } else { 'sha净化' }
    Write-Host ("  {0} -> {1}  {2}  [{3}]" -f $c.Substring(0, 7), $newCommit.sha.Substring(0, 7), (($message -split "`n")[0]), $mark)
    $remoteByLocal[$c] = $newCommit.sha
}

$head = $remoteByLocal[$commits[-1]]
Write-Host "== 更新 refs/heads/$Branch =="
try {
    Invoke-GhApi 'POST' "/repos/$Owner/$Repo/git/refs" @{ ref = "refs/heads/$Branch"; sha = $head } | Out-Null
} catch {
    Invoke-GhApi 'PATCH' "/repos/$Owner/$Repo/git/refs/heads/$Branch" @{ sha = $head; force = $true } | Out-Null
}
Remove-Item -Recurse -Force $tmpDir
Write-Host "完成：$Owner/$Repo @$Branch = $head"