# Ollama API 테스트 스크립트
Write-Host "Ollama LLM API Test" -ForegroundColor Cyan
Write-Host "=" * 50 -ForegroundColor Cyan
Write-Host ""

# 테스트 프롬프트
$testData = @{
    model = "llama3.1:8b"
    prompt = "Extract insurance terms from this text: Insurance Period: Whole Life, Payment Period: 10, 15, 20, 30 years. Return only: insuTerm, payTerm"
    stream = $false
    options = @{
        temperature = 0.1
        num_predict = 100
    }
} | ConvertTo-Json -Depth 10

Write-Host "Testing Llama 3.1..." -ForegroundColor Yellow
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

try {
    $response = Invoke-RestMethod -Uri "http://localhost:11434/api/generate" `
        -Method Post `
        -Body $testData `
        -ContentType "application/json" `
        -TimeoutSec 30
    
    $stopwatch.Stop()
    
    Write-Host "Success!" -ForegroundColor Green
    Write-Host "Response Time: $($stopwatch.Elapsed.TotalSeconds) seconds" -ForegroundColor Cyan
    Write-Host "Model: $($response.model)" -ForegroundColor Cyan
    Write-Host "Response:" -ForegroundColor Cyan
    Write-Host $response.response -ForegroundColor White
    
} catch {
    $stopwatch.Stop()
    Write-Host "Failed!" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=" * 50 -ForegroundColor Cyan


