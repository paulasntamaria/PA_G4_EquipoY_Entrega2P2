<#
.SYNOPSIS
  Simula eventos Kafka para probar los consumidores. Ejecutar desde ProposalBackend.

.DESCRIPTION
  -Tipo duplicado  Re-publica un evento ya enviado; los consumidores deben ignorarlo.
  -Tipo invalido   Publica un mensaje que no es JSON; debe ir al .DLT.
  -Tipo sin-id     Publica un JSON sin eventId; debe ir al .DLT.

.EXAMPLE
  .\tools\simular_evento.ps1 -Tipo duplicado -Buscar PRESTAMO_CREADO
#>
param(
	[Parameter(Mandatory = $true)][ValidateSet("duplicado", "invalido", "sin-id")][string]$Tipo,
	[string]$Topico = "eter.prestamos.eventos.v1",
	[string]$Buscar = ""
)

$ErrorActionPreference = "Continue"
$kafka = "/opt/kafka/bin"

# El mensaje va en base64 para que PowerShell 5.1 no altere el JSON.
function Publicar([string]$key, [string]$valor) {
	$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$key|$valor"))
	docker exec eter-kafka sh -c "echo $b64 | base64 -d | $kafka/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic $Topico --property parse.key=true --property key.separator='|'"
}

switch ($Tipo) {
	"duplicado" {
		Write-Host "Leyendo $Topico para encontrar el evento original..." -ForegroundColor Cyan
		$lineas = docker exec eter-kafka $kafka/kafka-console-consumer.sh `
			--bootstrap-server localhost:19092 --topic $Topico --from-beginning `
			--property print.key=true --property "key.separator=|" --timeout-ms 8000 2>$null
		$original = $lineas | Where-Object { $_ -like "*$Buscar*" -and $_ -like "*|{*" } | Select-Object -First 1
		if (-not $original) {
			Write-Host "No se encontro ningun evento que contenga '$Buscar' en $Topico." -ForegroundColor Red
			exit 1
		}
		$original = $original.TrimStart([char]0xFEFF)
		$sep = $original.IndexOf("|")
		$key = $original.Substring(0, $sep)
		$valor = $original.Substring($sep + 1)
		$eventId = ($valor | ConvertFrom-Json).eventId
		Write-Host "Re-publicando el MISMO evento $eventId con key $key" -ForegroundColor Yellow
		Publicar $key $valor
		Write-Host "Listo. Los consumidores deben registrar DUPLICADO y no crear nada nuevo." -ForegroundColor Green
		Write-Output $eventId
	}
	"invalido" {
		Write-Host "Publicando un mensaje que NO es JSON en $Topico..." -ForegroundColor Yellow
		Publicar "900123456-1:AF-SIMULADO" "esto-no-es-json {"
		Write-Host "Listo. Debe aparecer en $Topico.DLT (sin reintentos)." -ForegroundColor Green
	}
	"sin-id" {
		Write-Host "Publicando un JSON sin eventId en $Topico..." -ForegroundColor Yellow
		Publicar "900123456-1:AF-SIMULADO" '{"eventType":"PRESTAMO_CREADO","nitUniv":"900123456-1","placaInventario":"AF-SIMULADO"}'
		Write-Host "Listo. Debe aparecer en $Topico.DLT (sin reintentos)." -ForegroundColor Green
	}
}
