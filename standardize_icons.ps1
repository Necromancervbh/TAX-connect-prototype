# Icon Standardization Script
# This script standardizes all icon references by:
# 1. Removing _material3 suffix from all layout file references
# 2. Keeping the Material3 icon files (better formatted)
# 3. Deleting legacy duplicate icon files

Write-Host "=== TaxConnect Icon Standardization ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Update all layout files
Write-Host "Step 1: Updating layout file references..." -ForegroundColor Yellow

$layoutPath = "d:\mediascroll\app\src\main\res\layout"
$layoutFiles = Get-ChildItem $layoutPath -Filter "*.xml"
$totalUpdates = 0

foreach ($file in $layoutFiles) {
    $content = Get-Content $file.FullName -Raw
    $originalContent = $content
    
    # Replace all _material3 suffixes in drawable references
    $content = $content -replace '@drawable/ic_([a-z_]+)_material3', '@drawable/ic_$1'
    
    if ($content -ne $originalContent) {
        Set-Content $file.FullName -Value $content -NoNewline
        Write-Host "  ✓ Updated: $($file.Name)" -ForegroundColor Green
        $totalUpdates++
    }
}

Write-Host "  Updated $totalUpdates layout files" -ForegroundColor Cyan
Write-Host ""

# Step 2: Rename Material3 icon files (remove _material3 suffix)
Write-Host "Step 2: Renaming Material3 icon files..." -ForegroundColor Yellow

$drawablePath = "d:\mediascroll\app\src\main\res\drawable"
$material3Icons = Get-ChildItem $drawablePath -Filter "ic_*_material3.xml"
$renamedCount = 0

foreach ($icon in $material3Icons) {
    $newName = $icon.Name -replace '_material3\.xml$', '.xml'
    $newPath = Join-Path $drawablePath $newName
    $legacyPath = $newPath
    
    # Check if legacy version exists
    if (Test-Path $legacyPath) {
        # Delete legacy version first
        Remove-Item $legacyPath -Force
        Write-Host "  ✓ Deleted legacy: $newName" -ForegroundColor Gray
    }
    
    # Rename Material3 version
    Rename-Item $icon.FullName -NewName $newName -Force
    Write-Host "  ✓ Renamed: $($icon.Name) → $newName" -ForegroundColor Green
    $renamedCount++
}

Write-Host "  Renamed $renamedCount icon files" -ForegroundColor Cyan
Write-Host ""

# Step 3: Verify no broken references
Write-Host "Step 3: Verifying icon references..." -ForegroundColor Yellow

$allLayouts = Get-ChildItem $layoutPath -Filter "*.xml"
$brokenRefs = @()

foreach ($file in $allLayouts) {
    $content = Get-Content $file.FullName -Raw
    $iconRefs = [regex]::Matches($content, '@drawable/(ic_[a-z_0-9]+)')
    
    foreach ($match in $iconRefs) {
        $iconName = $match.Groups[1].Value + ".xml"
        $iconPath = Join-Path $drawablePath $iconName
        
        if (-not (Test-Path $iconPath)) {
            $brokenRefs += "$($file.Name): $iconName"
        }
    }
}

if ($brokenRefs.Count -eq 0) {
    Write-Host "  ✓ All icon references are valid!" -ForegroundColor Green
} else {
    Write-Host "  ⚠ Found $($brokenRefs.Count) broken references:" -ForegroundColor Red
    $brokenRefs | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
}

Write-Host ""
Write-Host "=== Standardization Complete ===" -ForegroundColor Cyan
Write-Host "Summary:" -ForegroundColor White
Write-Host "  - Layout files updated: $totalUpdates" -ForegroundColor White
Write-Host "  - Icon files renamed: $renamedCount" -ForegroundColor White
Write-Host "  - Broken references: $($brokenRefs.Count)" -ForegroundColor White
Write-Host ""
Write-Host "Next: Build the app to verify everything works!" -ForegroundColor Yellow
