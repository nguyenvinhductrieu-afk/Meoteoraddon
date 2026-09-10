$folders = @(
    "C:\Users\admin\.gradle\caches",
    "C:\Users\admin\.gradle\daemon",
    "C:\Users\admin\AppData\Local\Temp",
    "C:\Windows\Temp",
    "C:\Users\admin\Downloads",
    "C:\Users\admin\.m2"
)

Write-Host "Đang quét dung lượng các thư mục rác (có thể mất 1-2 phút)..."

$results = @()

foreach ($f in $folders) {
    if (Test-Path $f) {
        $size = (Get-ChildItem -Path $f -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
        $sizeMB = [math]::Round($size / 1MB, 2)
        $results += [PSCustomObject]@{
            Folder = $f
            SizeMB = $sizeMB
        }
    } else {
        $results += [PSCustomObject]@{
            Folder = $f
            SizeMB = 0
        }
    }
}

$results | Sort-Object SizeMB -Descending | Format-Table -AutoSize
