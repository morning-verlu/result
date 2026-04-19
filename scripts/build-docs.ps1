# build-docs.ps1
# Build docs markdown files into a single PDF.

param(
    [string]$OutputDir = "docs\dist",
    [string]$OutputFile = "Verlu-Manual.pdf",
    [string]$DocsDir = "docs",
    [string]$DocSet = "all",
    [string]$Title = "Verlu User Manual",
    [string]$Author = "Verlu Team",
    [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
    [string]$CJKFont = "Microsoft YaHei"
)

function Resolve-Executable {
    param(
        [string]$Name,
        [string[]]$Candidates = @()
    )

    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) {
        return $cmd.Source
    }

    foreach ($p in $Candidates) {
        if (Test-Path $p) {
            return $p
        }
    }

    # Winget packages are sometimes installed without PATH refresh in current shell.
    if ($Name -eq "pandoc") {
        $wgPandoc = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter "pandoc.exe" -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
        if ($wgPandoc) {
            return $wgPandoc
        }
    }

    return $null
}

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Resolve-EdgeExecutable {
    return Resolve-Executable -Name "msedge" -Candidates @(
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    )
}

if ($DocSet -ne "all" -and $DocSet -ne "user") {
    Write-Host "Invalid DocSet value. Use 'all' or 'user'." -ForegroundColor Red
    exit 1
}

$pandocExe = Resolve-Executable -Name "pandoc" -Candidates @(
    "$env:LOCALAPPDATA\Microsoft\WinGet\Links\pandoc.exe",
    "$env:ProgramFiles\Pandoc\pandoc.exe",
    "${env:ProgramFiles(x86)}\Pandoc\pandoc.exe"
)

if (-not $pandocExe) {
    Write-Host "Pandoc not found." -ForegroundColor Red
    Write-Host "Install with: winget install --id JohnMacFarlane.Pandoc -e"
    Write-Host "Install PDF engine: winget install --id MiKTeX.MiKTeX -e"
    exit 1
}

$isAdmin = Test-IsAdministrator
$edgeExe = Resolve-EdgeExecutable

$xelatexExe = Resolve-Executable -Name "xelatex" -Candidates @(
    "$env:LOCALAPPDATA\Programs\MiKTeX\miktex\bin\x64\xelatex.exe",
    "$env:ProgramFiles\MiKTeX\miktex\bin\x64\xelatex.exe",
    "${env:ProgramFiles(x86)}\MiKTeX\miktex\bin\x64\xelatex.exe"
)

$useEdgePdf = $false
if ($isAdmin) {
    if (-not $edgeExe) {
        Write-Host "Running as admin and Edge not found." -ForegroundColor Red
        Write-Host "Cannot use xelatex in admin shell. Install Edge or run from non-admin shell."
        exit 1
    }
    $useEdgePdf = $true
}

if (-not $useEdgePdf -and -not $xelatexExe) {
    if ($edgeExe) {
        $useEdgePdf = $true
    } else {
        Write-Host "xelatex not found." -ForegroundColor Red
        Write-Host "Install MiKTeX: winget install --id MiKTeX.MiKTeX -e"
        exit 1
    }
}

if ($DocSet -eq "user") {
    if ($OutputFile -eq "Verlu-Manual.pdf") {
        $OutputFile = "Verlu-User-Guide.pdf"
    }
    if ($Title -eq "Verlu User Manual") {
        $Title = "Verlu User Guide"
    }
    $docFiles = @(
        "$DocsDir\USER_GUIDE.md"
    )
} else {
    $docFiles = @(
        "$DocsDir\00-overview.md",
        "$DocsDir\01-quick-start.md",
        "$DocsDir\02-architecture.md",
        "$DocsDir\03-sync.md",
        "$DocsDir\04-doctor.md",
        "$DocsDir\05-talk.md",
        "$DocsDir\06-cnchess.md",
        "$DocsDir\07-cloud.md",
        "$DocsDir\08-admin-web.md",
        "$DocsDir\09-supabase.md",
        "$DocsDir\10-cross-module-flows.md"
    )
}

$missing = $docFiles | Where-Object { -not (Test-Path $_) }
if ($missing) {
    Write-Host "Missing docs files:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  $_" }
    exit 1
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$outputPath = [System.IO.Path]::GetFullPath((Join-Path $OutputDir $OutputFile))
if (Test-Path $outputPath) {
    Remove-Item $outputPath -Force -ErrorAction SilentlyContinue
}

Write-Host "Building PDF..."
Write-Host "DocSet: $DocSet"
Write-Host "Output: $outputPath"
Write-Host "Pandoc: $pandocExe"
if ($useEdgePdf) {
    Write-Host "PDF Engine: Edge headless"
    Write-Host "Edge: $edgeExe"
} else {
    Write-Host "PDF Engine: XeLaTeX"
    Write-Host "XeLaTeX: $xelatexExe"
}

if ($useEdgePdf) {
    $tmpHtml = Join-Path $OutputDir "__manual_tmp.html"
    $pandocHtmlArgs = @(
        $docFiles
        "--output=$tmpHtml"
        "--standalone"
        "--metadata=title:$Title"
        "--metadata=author:$Author"
        "--metadata=date:$Date"
        "--metadata=lang:zh-CN"
        "--toc"
        "--toc-depth=3"
        "--number-sections"
    )
    & $pandocExe @pandocHtmlArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "HTML build failed with exit code $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }

    $htmlUri = [System.Uri]::new((Resolve-Path $tmpHtml)).AbsoluteUri
    & $edgeExe "--headless=new" "--disable-gpu" "--print-to-pdf=$outputPath" $htmlUri
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Edge PDF print failed with exit code $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }
    $maxWaitSeconds = 30
    $elapsed = 0
    while (-not (Test-Path $outputPath) -and $elapsed -lt $maxWaitSeconds) {
        Start-Sleep -Seconds 1
        $elapsed += 1
    }
    if (-not (Test-Path $outputPath)) {
        Write-Host "Edge did not produce output PDF within ${maxWaitSeconds}s." -ForegroundColor Red
        Write-Host "Check temp HTML: $tmpHtml"
        exit 1
    }
    Write-Host "Temp HTML kept for debug: $tmpHtml"
} else {
    $pandocArgs = @(
        $docFiles
        "--output=$outputPath"
        "--pdf-engine=$xelatexExe"
        "--metadata=title:$Title"
        "--metadata=author:$Author"
        "--metadata=date:$Date"
        "--metadata=lang:zh-CN"
        "-V", "CJKmainfont=$CJKFont"
        "-V", "CJKmonofont=Consolas"
        "-V", "mainfont=$CJKFont"
        "-V", "geometry:margin=2.5cm"
        "-V", "linestretch=1.5"
        "-V", "fontsize=11pt"
        "-V", "colorlinks=true"
        "-V", "linkcolor=blue"
        "-V", "urlcolor=blue"
        "--toc"
        "--toc-depth=3"
        "--number-sections"
        "--syntax-highlighting=short"
        "-V", "documentclass=report"
    )

    & $pandocExe @pandocArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "PDF build failed with exit code $LASTEXITCODE" -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $outputPath)) {
        Write-Host "PDF engine finished but output file is missing: $outputPath" -ForegroundColor Red
        exit 1
    }
}

$fileSize = [math]::Round((Get-Item $outputPath).Length / 1KB, 1)
Write-Host "PDF build success." -ForegroundColor Green
Write-Host "Path: $outputPath"
Write-Host "Size: ${fileSize} KB"
