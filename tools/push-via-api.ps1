# push-via-api.ps1 —— git 传输被网络阻断时，用 GitHub Git Data API 完整回放提交历史
# 用法: tools/push-via-api.ps1 -Repo material-mail-full
# 原理: blob/tree/commit 均为内容寻址 SHA-1，API 重建的对象与本地 SHA 完全一致，
#       因此推送后本地 git fetch 可直接认亲，历史无损。
param(
    [Parameter(Mandatory = $true)][string]$Repo,
    [string]$Owner = 'Tangmjiu',
    [string]$Branch = 'main'
)
$ErrorActionPreference = 'Stop'
$tmpDir = Join-Path $env:TEMP ('gh-api-push-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

function Invoke-GhApi($method, $path, $bodyObject) {
    $jsonFile = Join-Path $tmpDir 'body.json'
    [System.IO.File]::WriteAllText($jsonFile, ($bodyObject | ConvertTo-Json -Depth 10 -Compress), [System.Text.UTF8Encoding]::new($false))
    $out = gh api -X $method $path --input $jsonFile 2>&1
    if ($LASTEXITCODE -ne 0) { throw "gh api $method $path 失败: $out" }
    return ($out | ConvertFrom-Json)
}

Write-Host "== 收集提交与 blob =="
$commits = git rev-list --reverse $Branch
$blobShas = [ordered]@{}
foreach ($c in $commits) {
    git ls-tree -r $c | ForEach-Object {
        $parts = $_ -split "`t", 2
        $meta = $parts[0] -split ' '
        if ($meta[1] -eq 'blob') { $blobShas[$meta[2]] = $true }
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
    if ($i % 20 -eq 0) { Write-Host "  $i / $($blobShas.Count)" }
}

Write-Host "== 逐提交重建 tree + commit =="
$parent = $null
foreach ($c in $commits) {
    $entries = @()
    git ls-tree -r $c | ForEach-Object {
        $parts = $_ -split "`t", 2
        $meta = $parts[0] -split ' '
        $entries += @{ path = $parts[1]; mode = $meta[0]; type = $meta[1]; sha = $meta[2] }
    }
    $tree = Invoke-GhApi 'POST' "/repos/$Owner/$Repo/git/trees" @{ tree = $entries }

    $metaLine = git show -s --format='%an%n%ae%n%aI%n%cn%n%ce%n%cI' $c
    $m = $metaLine -split "`n"
    $message = (git log -1 --format=%B $c) -join "`n"
    $commitBody = @{
        message = $message
        tree = $tree.sha
        parents = @($parent | Where-Object { $_ })
        author = @{ name = $m[0]; email = $m[1]; date = $m[2] }
        committer = @{ name = $m[3]; email = $m[4]; date = $m[5] }
    }
    $newCommit = Invoke-GhApi 'POST' "/repos/$Owner/$Repo/git/commits" $commitBody
    Write-Host ("  {0} -> {1}  {2}" -f $c.Substring(0, 7), $newCommit.sha.Substring(0, 7), (($message -split "`n")[0]))
    if ($newCommit.sha -ne $c) { Write-Warning "SHA 不一致：本地 $c vs 远端 $($newCommit.sha)（内容相同则无影响）" }
    $parent = $newCommit.sha
}

Write-Host "== 更新 refs/heads/$Branch =="
try {
    Invoke-GhApi 'POST' "/repos/$Owner/$Repo/git/refs" @{ ref = "refs/heads/$Branch"; sha = $parent } | Out-Null
} catch {
    Invoke-GhApi 'PATCH' "/repos/$Owner/$Repo/git/refs/heads/$Branch" @{ sha = $parent; force = $true } | Out-Null
}
Remove-Item -Recurse -Force $tmpDir
Write-Host "完成：$Owner/$Repo @$Branch = $parent"