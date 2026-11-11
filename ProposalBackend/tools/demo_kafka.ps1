<#
.SYNOPSIS
  Demostracion del flujo Kafka de ETER. Ejecutar desde ProposalBackend con Docker Compose levantado.

.PARAMETER SinRedisCaido
  Omite la prueba de apagar Redis.
#>
param([switch]$SinRedisCaido)

# En PowerShell 5.1 "Stop" convierte el stderr de docker en errores.
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$nit = "900123456-1"
$placa = "AF-DEMO-" + (Get-Date -Format "MMddHHmmss")
$kafka = "/opt/kafka/bin"
$raiz = Split-Path -Parent $PSScriptRoot

# Utilidades
function Titulo([string]$texto) {
	Write-Host ""
	Write-Host ("=" * 78) -ForegroundColor DarkGreen
	Write-Host " $texto" -ForegroundColor Green
	Write-Host ("=" * 78) -ForegroundColor DarkGreen
}
function Nota([string]$texto) { Write-Host $texto -ForegroundColor Cyan }

# Imprime en orden.
function Ver { $input | Out-String -Width 220 | ForEach-Object { $_.TrimEnd() } | Write-Host }

# Decodifica las respuestas como UTF-8 (PowerShell 5.1 usa Latin-1).
function Api([string]$metodo, [string]$url, $cuerpo = $null) {
	$p = @{ Method = $metodo; Uri = $url; Headers = $H; UseBasicParsing = $true }
	if ($cuerpo) {
		$p.Body = [Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json))
		$p.ContentType = "application/json; charset=utf-8"
	}
	$r = Invoke-WebRequest @p
	$texto = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
	if ($texto) { $texto | ConvertFrom-Json }
}

function Sql([string]$consulta) {
	docker exec eter-postgres psql -U postgres -d eterdb -c $consulta | Ver
}

function EsperarHasta([scriptblock]$condicion, [int]$segundos = 40) {
	$limite = (Get-Date).AddSeconds($segundos)
	while ((Get-Date) -lt $limite) {
		if (& $condicion) { return }
		Start-Sleep -Seconds 2
	}
	Write-Host "  (tiempo de espera agotado)" -ForegroundColor Red
}

function Logs([string]$contenedor, [string]$patron, [int]$ultimas = 3) {
	docker logs $contenedor 2>&1 | Select-String -Pattern $patron | Select-Object -Last $ultimas |
		ForEach-Object { $l = $_.Line -replace "\s+---\s+\[[^\]]+\]\s+\[[^\]]+\]\s+", " "; "[$contenedor] $l" } | Ver
}

$token = (python "$raiz\tools\generar_token.py").Trim()
$H = @{ Authorization = "Bearer $token" }

function Movimientos {
	foreach ($m in (Api GET "http://localhost:8089/api/v1/activos/movimientos-inventario?nitUniv=$nit&placaInventario=$placa")) { $m }
}
function Bandeja {
	foreach ($n in (Api GET "http://localhost:8090/api/v1/notificaciones?size=100").items) {
		if ($n.placaInventario -eq $placa) { $n }
	}
}
function VerKardex {
	@(Movimientos) | Sort-Object idMovimiento | Select-Object idMovimiento, tipoMovimiento, fechaMovimiento, numDocumento | Format-Table -AutoSize | Ver
}
function VerBandeja {
	@(Bandeja) | Select-Object tipo, titulo, leida, creadaEn | Format-Table -AutoSize | Ver
}

Titulo "EVIDENCIA 1. Contenedores levantados con Docker Compose"
Push-Location "$raiz\infrastructure"
docker compose ps --format "table {{.Name}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}" | Ver
Pop-Location

Titulo "EVIDENCIA 2. Topicos Kafka del diseno"
foreach ($t in "eter.prestamos.eventos.v1", "eter.activos.eventos.v1") {
	docker exec eter-kafka $kafka/kafka-topics.sh --bootstrap-server localhost:19092 --describe --topic $t 2>$null |
		Where-Object { $_ -match "PartitionCount" } | ForEach-Object { ($_ -split "\t")[0, 2, 3] -join "   " } | Ver
}
Nota "Todos los topicos del broker:"
docker exec eter-kafka $kafka/kafka-topics.sh --bootstrap-server localhost:19092 --list 2>$null | Ver

Titulo "EVIDENCIA 3. POST activo $placa -> ACTIVO_CREADO -> inventario registra ALTA"
Api POST "http://localhost:8087/api/v1/activos/activos-fijos" @{
	nitUniv = $nit; placaInventario = $placa; descripcion = "Portatil Lenovo ThinkPad T14 (demo Kafka)"
	codUnspsc = "43211503"; codSede = "SED-01"; valorContable = 4200000; estado = "ACTIVO"
} | Format-List | Ver
Nota "Esperando que el Outbox publique (cada 5 s) y el consumidor procese..."
EsperarHasta { @(Movimientos | Where-Object { $_.tipoMovimiento -eq "ALTA" }).Count -gt 0 }
Logs eter-activos "ACTIVO_CREADO\) publicado" 1
Logs eter-inventario "ALTA del activo $placa" 1
Nota "Kardex del activo en inventario:"
VerKardex

Titulo "EVIDENCIA 4. POST prestamo (ya vencido) -> PRESTAMO_CREADO -> 2 consumidores"
$prestamo = Api POST "http://localhost:8088/api/v1/activos/prestamos" @{
	nitUniv = $nit; placaInventario = $placa; numDocumento = "79123456"
	fechaSalida = (Get-Date).AddDays(-3).ToString("yyyy-MM-ddTHH:mm:ss")
	fechaDevolucionPactada = (Get-Date).AddDays(-1).ToString("yyyy-MM-ddTHH:mm:ss")
}
$prestamo | Format-List | Ver
$idPrestamo = $prestamo.idPrestamo
EsperarHasta { @(Movimientos | Where-Object { $_.tipoMovimiento -eq "PRESTAMO" }).Count -gt 0 }
EsperarHasta { @(Bandeja | Where-Object { $_.tipo -eq "PRESTAMO_CREADO" }).Count -gt 0 }
Logs eter-prestamos "PRESTAMO_CREADO\) publicado" 1
Logs eter-inventario "PRESTAMO del activo $placa" 1
Logs eter-notificaciones "recibido .*PRESTAMO_CREADO" 1
Nota "Inventario (grupo eter-inventario-v1) - kardex:"
VerKardex
Nota "Notificaciones (grupo eter-notificaciones-v2) - bandeja de 79123456@eter.edu.co:"
VerBandeja

Titulo "EVIDENCIA 5. El vigia detecta el vencimiento -> PRESTAMO_VENCIDO"
Nota "El vigia corre cada 30 s en Docker; esperando..."
EsperarHasta { @(Bandeja | Where-Object { $_.tipo -eq "PRESTAMO_VENCIDO" }).Count -gt 0 } 90
Logs eter-prestamos "PRESTAMO_VENCIDO" 2
Logs eter-inventario "PRESTAMO_VENCIDO\] no genera" 1
@(Bandeja) | Where-Object { $_.tipo -eq "PRESTAMO_VENCIDO" } | Select-Object tipo, titulo, mensaje | Format-List | Ver

Titulo "EVIDENCIA 6. PATCH devolucion -> PRESTAMO_DEVUELTO -> DEVOLUCION + notificacion"
Api PATCH "http://localhost:8088/api/v1/activos/prestamos/$idPrestamo/devolucion" @{
	fechaDevolucionReal = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
} | Select-Object idPrestamo, placaInventario, estado, fechaDevolucionReal | Format-List | Ver
EsperarHasta { @(Movimientos | Where-Object { $_.tipoMovimiento -eq "DEVOLUCION" }).Count -gt 0 }
EsperarHasta { @(Bandeja | Where-Object { $_.tipo -eq "PRESTAMO_DEVUELTO" }).Count -gt 0 }
Nota "Kardex completo del activo (todo lo registro Kafka, nadie lo digito):"
VerKardex
Nota "Bandeja del custodio:"
VerBandeja

Titulo "EVIDENCIA 7. Outbox publicado y claves de idempotencia en Redis"
Sql "SELECT event_type, aggregate_key, published_at IS NOT NULL AS publicado, publish_attempts FROM prestamos.outbox_event WHERE placa_inventario='$placa' ORDER BY occurred_at;"
Sql "SELECT event_type, aggregate_key, published_at IS NOT NULL AS publicado, publish_attempts FROM activos.outbox_event WHERE placa_inventario='$placa';"
Nota "Claves en Redis (eter:idem:{grupo}:{eventId}):"
$ids = docker exec eter-postgres psql -U postgres -d eterdb -At -c "SELECT event_id FROM prestamos.outbox_event WHERE placa_inventario='$placa' UNION ALL SELECT event_id FROM activos.outbox_event WHERE placa_inventario='$placa';"
$filas = foreach ($id in $ids) {
	foreach ($clave in (docker exec eter-redis redis-cli --scan --pattern "eter:idem:*:$id")) {
		"{0,-72} {1,-10} TTL={2}s" -f $clave, (docker exec eter-redis redis-cli GET $clave), (docker exec eter-redis redis-cli TTL $clave)
	}
}
$filas | Ver

Titulo "EVIDENCIA 8. Simulador: se re-publica el mismo PRESTAMO_CREADO (duplicado)"
$antesMov = @(Movimientos).Count
$antesNot = @(Bandeja).Count
$eventoDuplicado = @(& "$PSScriptRoot\simular_evento.ps1" -Tipo duplicado -Buscar "PRESTAMO_CREADO*$idPrestamo")[-1]
Start-Sleep -Seconds 6
Logs eter-inventario "$eventoDuplicado" 2
Logs eter-notificaciones "$eventoDuplicado" 2
Write-Host ("Movimientos antes={0} despues={1}   |   Notificaciones antes={2} despues={3}" -f `
		$antesMov, @(Movimientos).Count, $antesNot, @(Bandeja).Count) -ForegroundColor Yellow

if (-not $SinRedisCaido) {
	Titulo "EVIDENCIA 9. Redis caido: PostgreSQL sigue frenando el duplicado"
	docker stop eter-redis | Out-Null
	Nota "Redis detenido. Se re-publica otra vez el mismo evento..."
	& "$PSScriptRoot\simular_evento.ps1" -Tipo duplicado -Buscar "PRESTAMO_CREADO*$idPrestamo" | Out-Null
	Start-Sleep -Seconds 10
	Logs eter-inventario "$eventoDuplicado" 3
	Logs eter-notificaciones "$eventoDuplicado" 3
	Write-Host ("Movimientos={0}  Notificaciones={1}  (sin cambios)" -f @(Movimientos).Count, @(Bandeja).Count) -ForegroundColor Yellow
	docker start eter-redis | Out-Null
	Nota "Redis encendido de nuevo."
}

Titulo "EVIDENCIA 10. Mensaje invalido -> DLT sin reintentos"
& "$PSScriptRoot\simular_evento.ps1" -Tipo invalido | Out-Null
Start-Sleep -Seconds 8
Logs eter-inventario "\.DLT:" 1
Logs eter-notificaciones "\.DLT:" 1
Nota "Ultimos mensajes de eter.prestamos.eventos.v1.DLT (una copia por grupo consumidor):"
docker exec eter-kafka $kafka/kafka-console-consumer.sh --bootstrap-server localhost:19092 `
	--topic eter.prestamos.eventos.v1.DLT --from-beginning --timeout-ms 6000 `
	--property print.key=true --property print.headers=true 2>$null | Where-Object { $_ -match "eter-consumer-group" } |
	Select-Object -Last 2 | ForEach-Object {
		$partes = $_ -split "\t"
		$grupo = [regex]::Match($partes[0], "eter-consumer-group:([^,]*)").Groups[1].Value
		$causa = [regex]::Match($partes[0], "eter-error:(.*?)(,[a-z_-]+:|$)").Groups[1].Value
		if ($causa.Length -gt 70) { $causa = $causa.Substring(0, 70) + "..." }
		"grupo={0}`n   key={1}  valor={2}`n   causa={3}" -f $grupo, $partes[1], $partes[2], $causa
	} | Ver

Titulo "EVIDENCIA 11. Grupos de consumidores al dia (LAG = 0)"
foreach ($g in "eter-inventario-v1", "eter-notificaciones-v2") {
	docker exec eter-kafka $kafka/kafka-consumer-groups.sh --bootstrap-server localhost:19092 `
		--describe --group $g 2>$null | Where-Object { $_ -match "^(GROUP|eter)" } |
		ForEach-Object { $c = $_ -split "\s+"; "{0,-24} {1,-27} {2,-10} {3,-15} {4,-15} {5}" -f $c[0], $c[1], $c[2], $c[3], $c[4], $c[5] } | Ver
}

Titulo "FIN. Placa: $placa  |  Prestamo: $idPrestamo"
