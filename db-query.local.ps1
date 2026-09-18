<#
Workspace-visible wrapper for Codex file mentions.

The real local DB query runner lives at .codex/db-query.local.ps1.
This wrapper intentionally contains no DB secrets; credentials stay in
.codex/db.local.properties.
#>

$ScriptPath = Join-Path $PSScriptRoot ".codex\db-query.local.ps1"

if (-not (Test-Path -LiteralPath $ScriptPath)) {
    Write-Error "DB query runner not found: $ScriptPath"
    exit 1
}

& $ScriptPath @args
exit $LASTEXITCODE
