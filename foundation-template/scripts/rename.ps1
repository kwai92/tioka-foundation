<#
  BAS-25 模板改名（PowerShell）：把目标工程（本脚本所在目录的父目录）内的目录名、文件名、
  文件内容中的字面占位符替换为新应用编码；跳过 target/、node_modules/、dist/、.git/。幂等。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z][a-z0-9-]*$')]
    [string]$App
)

$ErrorActionPreference = 'Stop'

# 用字符码组装字面占位符，使本脚本自身不含该字面串，从而被扫描时保持原样。
$Token = [string]([char]123) + 'app' + [string]([char]125)
$root = Split-Path -Parent $PSScriptRoot
$skipNames = @('target', 'node_modules', 'dist', '.git')

function Test-Skip([string]$full) {
    $rel = $full.Substring($root.Length).TrimStart('\', '/')
    foreach ($seg in ($rel -split '[\\/]')) {
        if ($skipNames -contains $seg) { return $true }
    }
    return $false
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$replaced = 0

# 1) 文件内容替换
$files = @(Get-ChildItem -LiteralPath $root -Recurse -File -Force |
    Where-Object { -not (Test-Skip $_.FullName) })
foreach ($file in $files) {
    $text = [System.IO.File]::ReadAllText($file.FullName, $utf8)
    if ($text.Contains($Token)) {
        [System.IO.File]::WriteAllText($file.FullName, $text.Replace($Token, $App), $utf8)
        $replaced++
    }
}

# 2) 文件名替换
foreach ($file in $files) {
    if ($file.Name.Contains($Token)) {
        Rename-Item -LiteralPath $file.FullName -NewName ($file.Name.Replace($Token, $App))
    }
}

# 3) 目录名替换（自底向上：先深后浅）
$dirs = @(Get-ChildItem -LiteralPath $root -Recurse -Directory -Force |
    Where-Object { -not (Test-Skip $_.FullName) -and $_.Name.Contains($Token) } |
    Sort-Object { $_.FullName.Length } -Descending)
foreach ($dir in $dirs) {
    Rename-Item -LiteralPath $dir.FullName -NewName ($dir.Name.Replace($Token, $App))
}

Write-Host "已替换文件内容：$replaced 个；新应用编码：$App"
