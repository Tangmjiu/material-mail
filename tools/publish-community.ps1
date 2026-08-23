# publish-community.ps1 —— 从私有完整仓库发布开源社区版
#
# 当前阶段（pro/ 尚不存在）：直接 git push oss main 即可，无需本脚本。
#
# pro/ 落地后使用本脚本：基于 git filter-repo 剔除 pro/ 及其历史，
# 生成干净的社区版提交历史后推送到公开仓库。
#
# 前置：pip install git-filter-repo
# 用法：tools/publish-community.ps1 [-WhatIf]

param([switch]$WhatIf)

$ErrorActionPreference = 'Stop'
$repoRoot = git rev-parse --show-toplevel
$workDir = Join-Path $env:TEMP ("material-mail-community-" + [guid]::NewGuid().ToString('N'))

Write-Host "1. 克隆私有仓库到临时目录：$workDir"
git clone --no-local $repoRoot $workDir | Out-Null
Push-Location $workDir
try {
    Write-Host "2. 剔除 pro/ 路径及其历史"
    git filter-repo --path pro --invert-paths

    Write-Host "3. 剔除本地/机器配置（如有误入）"
    git filter-repo --path local.properties --invert-paths --force

    if ($WhatIf) {
        Write-Host "WhatIf：跳过推送。检查目录：$workDir"
        return
    }

    Write-Host "4. 推送到公开社区仓库"
    git remote add oss https://github.com/Tangmjiu/material-mail.git
    git push oss main --force-with-lease
    Write-Host "完成。社区版已同步。"
} finally {
    Pop-Location
    if (-not $WhatIf) { Remove-Item -Recurse -Force $workDir }
}