<#
.SYNOPSIS
Makes the MobPlayer TV emulator reachable from a phone on the same LAN.
.DESCRIPTION
Run in Administrator PowerShell with the emulator and TV app already open.
Forwards LAN:18081 -> localhost:18080 -> Android:8080. No mDNS discovery.
The relay and firewall rule persist; rerun after restarting ADB/the emulator.
If your Wi-Fi address changes, remove the old relay before running again.
.EXAMPLE
powershell -NoProfile -ExecutionPolicy Bypass -File .\forward-tv-port.ps1
.EXAMPLE
.\forward-tv-port.ps1 -DeviceSerial emulator-5554 -ListenAddress 192.168.31.173
.NOTES
Cleanup (Administrator PowerShell; use the IP printed by the script):
  netsh interface portproxy delete v4tov4 listenaddress=<IP> listenport=18081
  Remove-NetFirewallRule -Name MobPlayerTv-Phone-18081
Remove the ADB forward only if no other tools need it:
  adb -s <SERIAL> forward --remove tcp:18080
Keep both devices on the same LAN, with client/guest isolation disabled.
Enter the printed WebSocket URL in your controller app, then the TV's PIN.
#>
[CmdletBinding()]
param(
    [string]$DeviceSerial,
    [string]$ListenAddress,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
if ($Help) {
    Get-Help $PSCommandPath -Full
    exit 0
}

$listenPort = 18081
$adbPort = 18080
$devicePort = 8080
$ruleName = 'MobPlayerTv-Phone-18081'
$ruleGroup = 'MobPlayer TV development'
$createdForward = $false
$createdProxy = $false
$createdFirewall = $false

function Invoke-Adb {
    param([string[]]$Arguments)
    $output = @(& $script:adbPath @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: adb $($Arguments -join ' ')" }
    return $output
}

function Invoke-Netsh {
    param([string[]]$Arguments)
    $output = @(& netsh.exe @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "netsh failed: $($output -join ' ')" }
    return $output
}

function Assert-WebSocket {
    param([string]$Address)
    $socket = New-Object System.Net.WebSockets.ClientWebSocket
    $timeout = New-Object System.Threading.CancellationTokenSource
    try {
        $socket.Options.Proxy = $null
        $timeout.CancelAfter(5000)
        $null = $socket.ConnectAsync([Uri]$Address, $timeout.Token).GetAwaiter().GetResult()
    } catch {
        throw "Cannot connect to $Address. Keep MobPlayer TV open in the selected emulator. $($_.Exception.Message)"
    } finally {
        $socket.Abort()
        $socket.Dispose()
        $timeout.Dispose()
    }
}

try {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Open PowerShell with Run as administrator, then run this script again.'
    }

    $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $adbPath = $adbCommand.Source
    } else {
        $sdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")
        $adbPath = $sdkRoots | Where-Object { $_ } | ForEach-Object {
            Join-Path $_ 'platform-tools\adb.exe'
        } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (-not $adbPath) { throw 'ADB not found. Install Android SDK Platform-Tools or add adb.exe to PATH.' }

    $deviceListing = @(Invoke-Adb -Arguments @('devices', '-l'))
    $onlineDevices = @($deviceListing | ForEach-Object {
        if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
    })
    if ($DeviceSerial) {
        if ($onlineDevices -notcontains $DeviceSerial) {
            throw "Device '$DeviceSerial' is not online/authorized. Check adb devices -l."
        }
    } else {
        $emulators = @($onlineDevices | Where-Object { $_ -like 'emulator-*' })
        if ($emulators.Count -ne 1) {
            throw "Expected one online emulator; found $($emulators.Count). Use -DeviceSerial. Online devices: $($onlineDevices -join ', ')"
        }
        $DeviceSerial = $emulators[0]
    }

    $addresses = @(Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
        $_.AddressState -eq 'Preferred' -and $_.IPAddress -notmatch '^(127\.|169\.254\.|0\.)'
    })
    if (-not $ListenAddress) {
        $wifiIndexes = @(Get-NetAdapter -Physical | Where-Object {
            $_.Status -eq 'Up' -and $_.NdisPhysicalMedium -in @(1, 9)
        } | Select-Object -ExpandProperty ifIndex)
        $wifiAddresses = @($addresses | Where-Object {
            $wifiIndexes -contains $_.InterfaceIndex
        } | Select-Object -ExpandProperty IPAddress -Unique)
        if ($wifiAddresses.Count -ne 1) {
            throw "Cannot select a unique Wi-Fi IPv4 address. Use -ListenAddress with your LAN IP. Available: $(($addresses.IPAddress) -join ', ')"
        }
        $ListenAddress = $wifiAddresses[0]
    }
    if (-not ($addresses | Where-Object { $_.IPAddress -eq $ListenAddress })) {
        throw "ListenAddress '$ListenAddress' is not an active local LAN IPv4 address."
    }

    $forwards = @(Invoke-Adb -Arguments @('forward', '--list'))
    $existingForward = @($forwards | Where-Object { $_ -match "^\S+\s+tcp:$adbPort\s+" })
    $expectedForward = '^' + [regex]::Escape($DeviceSerial) + "\s+tcp:$adbPort\s+tcp:$devicePort\s*$"
    if ($existingForward.Count -gt 0 -and ($existingForward.Count -ne 1 -or $existingForward[0] -notmatch $expectedForward)) {
        throw "ADB port $adbPort already forwards to another destination: $($existingForward -join ', ')"
    }

    # Parse only address/port rows; netsh headings are localized on Windows.
    $proxyRows = @(Invoke-Netsh -Arguments @('interface', 'portproxy', 'show', 'all'))
    $matchingProxy = $false
    foreach ($row in $proxyRows) {
        if ($row -match '^\s*(\S+)\s+(\d+)\s+(\S+)\s+(\d+)\s*$') {
            if ([int]$Matches[2] -eq $listenPort -and $Matches[1] -in @($ListenAddress, '0.0.0.0', '::')) {
                if ($Matches[1] -ne $ListenAddress -or $Matches[3] -ne '127.0.0.1' -or [int]$Matches[4] -ne $adbPort) {
                    throw "An unrelated portproxy rule conflicts with ${ListenAddress}:$listenPort."
                }
                $matchingProxy = $true
            }
        }
    }
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue)
    if (-not $matchingProxy -and ($listeners | Where-Object {
        $_.LocalPort -eq $listenPort -and $_.LocalAddress -in @($ListenAddress, '0.0.0.0', '::')
    })) { throw "${ListenAddress}:$listenPort is already occupied. Stop the conflicting listener first." }
    if ($existingForward.Count -eq 0 -and ($listeners | Where-Object { $_.LocalPort -eq $adbPort })) {
        throw "Port $adbPort is occupied by a listener that is not the expected ADB forward."
    }
    $existingRule = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
    if ($existingRule -and $existingRule.Group -ne $ruleGroup) {
        throw "Firewall rule '$ruleName' already exists outside this script's group; it will not be replaced."
    }

    if ($existingForward.Count -eq 0) {
        $null = Invoke-Adb -Arguments @('-s', $DeviceSerial, 'forward', '--no-rebind', "tcp:$adbPort", "tcp:$devicePort")
        $createdForward = $true
    }
    Assert-WebSocket -Address "ws://127.0.0.1:$adbPort/control"

    if ((Get-Service iphlpsvc).Status -ne 'Running') { Start-Service iphlpsvc }
    if (-not $matchingProxy) {
        $null = Invoke-Netsh -Arguments @('interface', 'portproxy', 'add', 'v4tov4',
            "listenaddress=$ListenAddress", "listenport=$listenPort", 'connectaddress=127.0.0.1', "connectport=$adbPort")
        $createdProxy = $true
    }
    $firewallOptions = @{
        Name = $ruleName; Direction = 'Inbound'; Action = 'Allow'; Enabled = 'True'
        Protocol = 'TCP'; LocalAddress = $ListenAddress; LocalPort = $listenPort
        RemoteAddress = 'LocalSubnet'; Profile = 'Any'
    }
    if ($existingRule) {
        Set-NetFirewallRule @firewallOptions
    } else {
        $null = New-NetFirewallRule @firewallOptions -DisplayName 'MobPlayer TV phone access' -Group $ruleGroup
        $createdFirewall = $true
    }

    $endpoint = "ws://${ListenAddress}:$listenPort/control"
    # IP Helper can take a moment to activate a new listener.
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        try { Assert-WebSocket -Address $endpoint; break } catch {
            if ($attempt -eq 2) { throw }
            Start-Sleep -Milliseconds 300
        }
    }
    Write-Host "`nReady: $endpoint" -ForegroundColor Green
    Write-Host "Device: $DeviceSerial | LAN:$listenPort -> localhost:$adbPort -> Android:$devicePort"
    Write-Host "On your phone, use manual connection and enter the TV's pairing PIN."
    Write-Host 'Both devices must share the same LAN. Automatic discovery is not provided.'
    Write-Host 'The relay persists after this window closes. Rerun after restarting ADB/the emulator.'
    Write-Host "Remove relay: netsh interface portproxy delete v4tov4 listenaddress=$ListenAddress listenport=$listenPort"
    Write-Host "Remove firewall rule: Remove-NetFirewallRule -Name $ruleName"
} catch {
    $failure = $_.Exception.Message
    # Roll back only resources created by this run, preserving existing forwarding.
    if ($createdFirewall) { Remove-NetFirewallRule -Name $ruleName -ErrorAction Continue }
    if ($createdProxy) {
        & netsh.exe interface portproxy delete v4tov4 "listenaddress=$ListenAddress" "listenport=$listenPort" | Out-Null
    }
    if ($createdForward) { & $adbPath -s $DeviceSerial forward --remove "tcp:$adbPort" | Out-Null }
    Write-Error $failure -ErrorAction Continue
    exit 1
}
