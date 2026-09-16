param(
    [Parameter(Mandatory)]
    [string] $Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$sourcePath = (Resolve-Path -LiteralPath $Path).Path
$previewPath = Join-Path (Split-Path $sourcePath -Parent) ([IO.Path]::GetFileNameWithoutExtension($sourcePath) + '.preview.jpg')
$source = [Drawing.Image]::FromFile($sourcePath)
$preview = $null
$graphics = $null
$encoding = $null
$output = $null
try {
    $scale = [Math]::Min(1.0, [Math]::Min(1920.0 / $source.Width, 1080.0 / $source.Height))
    $width = [Math]::Max(1, [int][Math]::Floor($source.Width * $scale))
    $height = [Math]::Max(1, [int][Math]::Floor($source.Height * $scale))
    $preview = New-Object Drawing.Bitmap($width, $height)
    $graphics = [Drawing.Graphics]::FromImage($preview)
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($source, 0, 0, $width, $height)
    $codec = [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
    $encoding = New-Object Drawing.Imaging.EncoderParameters(1)
    $encoding.Param[0] = New-Object Drawing.Imaging.EncoderParameter([Drawing.Imaging.Encoder]::Quality, [long]80)
    $output = [IO.File]::Create($previewPath)
    $preview.Save($output, $codec, $encoding)
    [PSCustomObject]@{
        Path = $previewPath
        Width = $width
        Height = $height
        Bytes = $output.Length
    }
} finally {
    if ($output) { $output.Dispose() }
    if ($encoding) { $encoding.Dispose() }
    if ($graphics) { $graphics.Dispose() }
    if ($preview) { $preview.Dispose() }
    $source.Dispose()
}
