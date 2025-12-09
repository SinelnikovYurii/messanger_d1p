# Скрипт для обновления объединённой документации
# Использование: .\update-docs.ps1

Write-Host "Обновление документации..." -ForegroundColor Cyan

$projectRoot = "D:\androidxx\messanger_dip1"
$docsApiPath = "$projectRoot\docs\api"

# Массив модулей
$modules = @(
    @{Name="authorization-service"; Path="Authorization_service"},
    @{Name="core-api-service"; Path="core-api-service"},
    @{Name="gateway"; Path="gateway"},
    @{Name="websocket-server"; Path="websocket_server"}
)

# Проверка существования директории docs/api
if (-not (Test-Path $docsApiPath)) {
    Write-Host "Создание директории $docsApiPath..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $docsApiPath | Out-Null
}

# Копирование документации каждого модуля
foreach ($module in $modules) {
    $sourcePath = "$projectRoot\$($module.Path)\target\site\apidocs"
    $destPath = "$docsApiPath\$($module.Name)"

    if (Test-Path $sourcePath) {
        Write-Host "Копирование документации $($module.Name)..." -ForegroundColor Green

        # Удаление старой документации если существует
        if (Test-Path $destPath) {
            Remove-Item -Path $destPath -Recurse -Force
        }

        # Копирование новой документации
        Copy-Item -Path $sourcePath -Destination $destPath -Recurse -Force
    }
    else {
        Write-Host "Документация для $($module.Name) не найдена в $sourcePath" -ForegroundColor Yellow
        Write-Host "Выполните сначала: mvn javadoc:javadoc в модуле $($module.Path)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Документация обновлена!" -ForegroundColor Green
Write-Host "Откройте docs\api\index.html в браузере для просмотра" -ForegroundColor Cyan

