param(
    [Parameter(Mandatory = $true)]
    [string]$InputDocx,

    [Parameter(Mandatory = $true)]
    [string]$OutputPdf
)

# 使用本机 Microsoft Word 的 COM 自动化进行无界面 PDF 导出。
# 该脚本仅用于视觉 QA，不修改源 DOCX，也不会在桌面或项目外创建其他文件。
$ErrorActionPreference = 'Stop'
$resolvedInput = (Resolve-Path -LiteralPath $InputDocx).Path
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPdf)
$outputDirectory = [System.IO.Path]::GetDirectoryName($resolvedOutput)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

$word = $null
$document = $null
try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0

    # Open 参数 ReadOnly=$true，避免 Word 自动保存兼容性或分页变化回源文件。
    $document = $word.Documents.Open($resolvedInput, $false, $true)

    # 17 对应 wdExportFormatPDF。0 对应 wdExportOptimizeForPrint，便于检查正文与表格。
    $document.ExportAsFixedFormat($resolvedOutput, 17, $false, 0)
}
finally {
    if ($null -ne $document) {
        $document.Close($false)
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($document)
    }
    if ($null -ne $word) {
        $word.Quit()
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($word)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

Get-Item -LiteralPath $resolvedOutput | Select-Object FullName, Length, LastWriteTime
