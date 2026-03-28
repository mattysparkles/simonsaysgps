$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function New-Brush([string]$hex) {
    return [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($hex))
}

function New-Pen([string]$hex, [float]$width) {
    $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml($hex), $width)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    return $pen
}

function Use-Graphics([System.Drawing.Graphics]$g) {
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
}

function New-RoundedRectPath([float]$x, [float]$y, [float]$width, [float]$height, [float]$radius) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $radius * 2

    if ($diameter -gt $width) { $diameter = $width }
    if ($diameter -gt $height) { $diameter = $height }
    $radius = $diameter / 2

    $path.AddArc($x, $y, $diameter, $diameter, 180, 90)
    $path.AddArc($x + $width - $diameter, $y, $diameter, $diameter, 270, 90)
    $path.AddArc($x + $width - $diameter, $y + $height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($x, $y + $height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Fill-RoundedRect($g, $brush, [float]$x, [float]$y, [float]$width, [float]$height, [float]$radius) {
    $path = New-RoundedRectPath $x $y $width $height $radius
    $g.FillPath($brush, $path)
    $path.Dispose()
}

function Stroke-RoundedRect($g, $pen, [float]$x, [float]$y, [float]$width, [float]$height, [float]$radius) {
    $path = New-RoundedRectPath $x $y $width $height $radius
    $g.DrawPath($pen, $path)
    $path.Dispose()
}

function Draw-LocationAntenna($g, $cx, $topY, $w, $h) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddBezier($cx, $topY, $cx + ($w * 0.45), $topY + ($h * 0.1), $cx + ($w * 0.5), $topY + ($h * 0.45), $cx, $topY + $h)
    $path.AddBezier($cx, $topY + $h, $cx - ($w * 0.5), $topY + ($h * 0.45), $cx - ($w * 0.45), $topY + ($h * 0.1), $cx, $topY)
    $g.FillPath((New-Brush "#FF5A4C"), $path)
    $g.FillEllipse((New-Brush "#FFF6EF"), $cx - ($w * 0.16), $topY + ($h * 0.24), $w * 0.32, $h * 0.32)
    $path.Dispose()
}

function Draw-SimonHead($g, $cx, $cy, $scale) {
    $headBrush = New-Brush "#18BEB7"
    $shadowBrush = New-Brush "#0C6B73"
    $eyeBrush = New-Brush "#FFB347"
    $pupilBrush = New-Brush "#2B190A"
    $lensBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(55, 255, 255, 255))
    $glassesPen = New-Pen "#111111" (6 * $scale)
    $mouthPen = New-Pen "#111111" (3 * $scale)
    $smirkPen = New-Pen "#3B1720" (4 * $scale)

    $headRect = [System.Drawing.RectangleF]::new($cx - (38 * $scale), $cy - (33 * $scale), 76 * $scale, 68 * $scale)
    $g.FillEllipse($shadowBrush, $headRect.X + (2 * $scale), $headRect.Y + (4 * $scale), $headRect.Width, $headRect.Height)
    $g.FillEllipse($headBrush, $headRect)

    $leftGlass = [System.Drawing.RectangleF]::new($cx - (32 * $scale), $cy - (12 * $scale), 28 * $scale, 23 * $scale)
    $rightGlass = [System.Drawing.RectangleF]::new($cx + (4 * $scale), $cy - (12 * $scale), 28 * $scale, 23 * $scale)
    Fill-RoundedRect $g $lensBrush $leftGlass.X $leftGlass.Y $leftGlass.Width $leftGlass.Height (6 * $scale)
    Fill-RoundedRect $g $lensBrush $rightGlass.X $rightGlass.Y $rightGlass.Width $rightGlass.Height (6 * $scale)
    Stroke-RoundedRect $g $glassesPen $leftGlass.X $leftGlass.Y $leftGlass.Width $leftGlass.Height (6 * $scale)
    Stroke-RoundedRect $g $glassesPen $rightGlass.X $rightGlass.Y $rightGlass.Width $rightGlass.Height (6 * $scale)
    $g.DrawLine($glassesPen, $cx - (4 * $scale), $cy - (1 * $scale), $cx + (4 * $scale), $cy - (1 * $scale))

    $g.FillEllipse($eyeBrush, $cx - (25 * $scale), $cy - (7 * $scale), 13 * $scale, 14 * $scale)
    $g.FillEllipse($eyeBrush, $cx + (12 * $scale), $cy - (7 * $scale), 13 * $scale, 14 * $scale)
    $g.FillEllipse($pupilBrush, $cx - (20 * $scale), $cy - (2 * $scale), 5.5 * $scale, 6 * $scale)
    $g.FillEllipse($pupilBrush, $cx + (17 * $scale), $cy - (2 * $scale), 5.5 * $scale, 6 * $scale)
    $g.DrawArc($mouthPen, $cx - (8 * $scale), $cy + (7 * $scale), 16 * $scale, 12 * $scale, 8, 164)
    $g.DrawArc($smirkPen, $cx + (2 * $scale), $cy + (10 * $scale), 16 * $scale, 10 * $scale, 5, 135)

    $headBrush.Dispose()
    $shadowBrush.Dispose()
    $eyeBrush.Dispose()
    $pupilBrush.Dispose()
    $lensBrush.Dispose()
    $glassesPen.Dispose()
    $mouthPen.Dispose()
    $smirkPen.Dispose()
}

function Draw-SimonIcon($g, $size, [bool]$transparentBackground) {
    $bgBrush = New-Brush "#FFF6DE"
    if (-not $transparentBackground) {
        $g.FillRectangle($bgBrush, 0, 0, $size, $size)
    }
    $scale = $size / 108.0
    if (-not $transparentBackground) {
        $halo = [System.Drawing.Drawing2D.GraphicsPath]::new()
        $halo.AddEllipse(8 * $scale, 8 * $scale, $size - (16 * $scale), $size - (16 * $scale))
        $g.FillPath((New-Brush "#FFF0BB"), $halo)
        $halo.Dispose()
    }

    Draw-LocationAntenna $g ($size * 0.5) (8 * $scale) (18 * $scale) (20 * $scale)
    Draw-SimonHead $g ($size * 0.5) (44 * $scale) $scale

    $bodyBrush = New-Brush "#18BEB7"
    $armBrush = New-Brush "#16A89F"
    $mapBrush = New-Brush "#FFFDF4"
    $mapPen = New-Pen "#1A6CFF" (2.4 * $scale)
    $outline = New-Pen "#0D2233" (2.2 * $scale)

    $g.FillEllipse($bodyBrush, $size * 0.25, $size * 0.57, $size * 0.5, $size * 0.23)
    $g.FillEllipse($armBrush, $size * 0.12, $size * 0.61, $size * 0.18, $size * 0.12)
    $g.FillEllipse($armBrush, $size * 0.70, $size * 0.6, $size * 0.18, $size * 0.12)

    $mapPoints = @(
        [System.Drawing.PointF]::new($size * 0.31, $size * 0.61),
        [System.Drawing.PointF]::new($size * 0.47, $size * 0.55),
        [System.Drawing.PointF]::new($size * 0.55, $size * 0.66),
        [System.Drawing.PointF]::new($size * 0.71, $size * 0.60),
        [System.Drawing.PointF]::new($size * 0.68, $size * 0.82),
        [System.Drawing.PointF]::new($size * 0.34, $size * 0.8)
    )
    $g.FillPolygon($mapBrush, $mapPoints)
    $g.DrawPolygon($outline, $mapPoints)
    $g.DrawLine($mapPen, $size * 0.37, $size * 0.65, $size * 0.47, $size * 0.7)
    $g.DrawLine($mapPen, $size * 0.47, $size * 0.7, $size * 0.54, $size * 0.63)
    $g.DrawLine($mapPen, $size * 0.54, $size * 0.63, $size * 0.64, $size * 0.72)
    $g.DrawLine($mapPen, $size * 0.42, $size * 0.77, $size * 0.52, $size * 0.74)
    $g.DrawLine($mapPen, $size * 0.52, $size * 0.74, $size * 0.61, $size * 0.79)

    $bodyBrush.Dispose()
    $armBrush.Dispose()
    $mapBrush.Dispose()
    $mapPen.Dispose()
    $outline.Dispose()
    $bgBrush.Dispose()
}

function Draw-SimonSplash($g, $size) {
    $bg = New-Brush "#FFF8E0"
    $g.FillRectangle($bg, 0, 0, $size, $size)
    $bg.Dispose()

    $scale = $size / 900.0
    Draw-LocationAntenna $g (430 * $scale) (85 * $scale) (95 * $scale) (110 * $scale)
    Draw-SimonHead $g (420 * $scale) (260 * $scale) (3.8 * $scale)

    $bodyBrush = New-Brush "#18BEB7"
    $armBrush = New-Brush "#16A89F"
    $handBrush = New-Brush "#FFD0A1"
    $shadowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(42, 7, 17, 31))
    $mapBrush = New-Brush "#FFFDF4"
    $mapPen = New-Pen "#1A6CFF" (10 * $scale)
    $outline = New-Pen "#102030" (8 * $scale)

    $g.FillEllipse($shadowBrush, 205 * $scale, 620 * $scale, 420 * $scale, 130 * $scale)
    $g.FillEllipse($bodyBrush, 240 * $scale, 430 * $scale, 310 * $scale, 240 * $scale)
    $g.FillEllipse($armBrush, 145 * $scale, 470 * $scale, 150 * $scale, 72 * $scale)
    $g.FillEllipse($armBrush, 520 * $scale, 500 * $scale, 150 * $scale, 72 * $scale)
    $g.FillEllipse($handBrush, 120 * $scale, 450 * $scale, 76 * $scale, 76 * $scale)
    $g.FillEllipse($handBrush, 640 * $scale, 472 * $scale, 76 * $scale, 76 * $scale)

    $mapPoints = @(
        [System.Drawing.PointF]::new(475 * $scale, 475 * $scale),
        [System.Drawing.PointF]::new(620 * $scale, 430 * $scale),
        [System.Drawing.PointF]::new(710 * $scale, 545 * $scale),
        [System.Drawing.PointF]::new(625 * $scale, 690 * $scale),
        [System.Drawing.PointF]::new(470 * $scale, 640 * $scale)
    )
    $g.FillPolygon($mapBrush, $mapPoints)
    $g.DrawPolygon($outline, $mapPoints)
    $g.DrawLine($mapPen, 512 * $scale, 500 * $scale, 585 * $scale, 540 * $scale)
    $g.DrawLine($mapPen, 585 * $scale, 540 * $scale, 645 * $scale, 490 * $scale)
    $g.DrawLine($mapPen, 535 * $scale, 615 * $scale, 605 * $scale, 585 * $scale)
    $g.DrawLine($mapPen, 605 * $scale, 585 * $scale, 655 * $scale, 630 * $scale)
    $g.DrawArc($outline, 300 * $scale, 590 * $scale, 110 * $scale, 55 * $scale, 210, 120)
    $g.DrawArc($outline, 415 * $scale, 605 * $scale, 120 * $scale, 50 * $scale, 210, 100)

    $bodyBrush.Dispose()
    $armBrush.Dispose()
    $handBrush.Dispose()
    $shadowBrush.Dispose()
    $mapBrush.Dispose()
    $mapPen.Dispose()
    $outline.Dispose()
}

function Save-Png([string]$path, [int]$size, [scriptblock]$drawer) {
    $directory = Split-Path -Parent $path
    if (!(Test-Path $directory)) { New-Item -ItemType Directory -Path $directory | Out-Null }
    $bitmap = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    Use-Graphics $g
    & $drawer $g $size
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bitmap.Dispose()
}

$root = Split-Path -Parent $PSScriptRoot
$appRes = Join-Path $root "app\src\main\res"

$legacy = @{
    "mipmap-mdpi" = 108
    "mipmap-hdpi" = 162
    "mipmap-xhdpi" = 216
    "mipmap-xxhdpi" = 324
    "mipmap-xxxhdpi" = 432
}

foreach ($entry in $legacy.GetEnumerator()) {
    $dir = Join-Path $appRes $entry.Key
    Save-Png (Join-Path $dir "ic_launcher.png") $entry.Value { param($g, $size) Draw-SimonIcon $g $size $false }
    Save-Png (Join-Path $dir "ic_launcher_round.png") $entry.Value { param($g, $size) Draw-SimonIcon $g $size $false }
}

Save-Png (Join-Path $appRes "drawable-nodpi\ic_launcher_foreground_art.png") 432 { param($g, $size) Draw-SimonIcon $g $size $true }
Save-Png (Join-Path $appRes "drawable-nodpi\simon_splash_intro.png") 900 { param($g, $size) Draw-SimonSplash $g $size }
Save-Png (Join-Path $root "branding\play-store-icon-512.png") 512 { param($g, $size) Draw-SimonIcon $g $size $false }
