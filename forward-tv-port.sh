#!/bin/bash
# macOS (including its built-in Bash 3.2): phone -> Mac:18081 -> ADB:18080 -> TV:8080.
# Run: bash ./forward-tv-port.sh [--device emulator-5554] [--listen-address LAN_IP]
# Prerequisites: Android SDK Platform-Tools and socat (brew install socat).
# Keep the TV app and this terminal open. Ctrl+C stops the relay and removes
# only the ADB forwarding created by this invocation. Existing forwards survive.
# If macOS Firewall blocks socat, allow it in System Settings > Network >
# Firewall > Options, or allow the incoming-connection prompt. Do not disable
# the firewall. Both devices must share a LAN without guest/client isolation.

set -euo pipefail

usage() {
    printf '%s\n' \
        'Usage: bash ./forward-tv-port.sh [--device SERIAL] [--listen-address IPv4]' \
        'Requires macOS, adb, and socat. Install socat with: brew install socat' \
        'Detects one online emulator and one active Wi-Fi IPv4 address.' \
        'Use --listen-address for Ethernet or when several addresses are available.' \
        'Forwards LAN:18081 -> localhost:18080 -> Android:8080.' \
        'Keep Terminal open; Ctrl+C stops forwarding. No sudo is needed.' \
        'Allows clients from the selected interface subnet. No automatic discovery.'
}

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

device_serial=''
listen_address=''
listen_port=18081
adb_port=18080
device_port=8080
created_forward=0
relay_pid=''

while [ "$#" -gt 0 ]; do
    case "$1" in
        --device|--listen-address)
            [ "$#" -ge 2 ] && [ -n "$2" ] || die "Missing value for $1"
            case "$2" in --*) die "Missing value for $1" ;; esac
            if [ "$1" = '--device' ]; then device_serial=$2; else listen_address=$2; fi
            shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) die "Unknown option: $1 (use --help)" ;;
    esac
done

[ "$(uname -s)" = 'Darwin' ] || die 'This script is for macOS. On Windows, use forward-tv-port.ps1.'

adb_bin=$(command -v adb || true)
if [ -z "$adb_bin" ]; then
    for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk"; do
        if [ -n "$sdk_root" ] && [ -x "$sdk_root/platform-tools/adb" ]; then
            adb_bin="$sdk_root/platform-tools/adb"
            break
        fi
    done
fi
[ -n "$adb_bin" ] || die 'ADB not found. Install Android SDK Platform-Tools or add adb to PATH.'
socat_bin=$(command -v socat || true)
if [ -z "$socat_bin" ]; then
    for candidate in /opt/homebrew/bin/socat /usr/local/bin/socat; do
        if [ -x "$candidate" ]; then socat_bin=$candidate; break; fi
    done
fi
[ -n "$socat_bin" ] || die 'socat not found. Install it with: brew install socat (https://brew.sh if Homebrew is missing).'

device_listing=$("$adb_bin" devices -l) || die 'Cannot list ADB devices.'
online_devices=$(printf '%s\n' "$device_listing" | awk '$2 == "device" {print $1}')
if [ -z "$device_serial" ]; then
    emulators=$(printf '%s\n' "$online_devices" | awk '/^emulator-/')
    count=$(printf '%s\n' "$emulators" | awk 'NF {n++} END {print n+0}')
    [ "$count" -eq 1 ] || die "Expected one online emulator; found $count. Use --device SERIAL. Check adb devices -l."
    device_serial=$emulators
else
    printf '%s\n' "$online_devices" | awk -v serial="$device_serial" '$0 == serial {found=1} END {exit !found}' \
        || die "Device '$device_serial' is not online/authorized. Check adb devices -l."
fi

if [ -z "$listen_address" ]; then
    hardware_ports=$(networksetup -listallhardwareports) || die 'Cannot enumerate network interfaces. Use --listen-address.'
    wifi_interfaces=$(printf '%s\n' "$hardware_ports" | awk '
        /^Hardware Port: / {wifi=($0 ~ /Wi-Fi|AirPort/)}
        /^Device: / && wifi {print $2}')
    candidate_count=0
    candidate_address=''
    for interface in $wifi_interfaces; do
        address=$(ipconfig getifaddr "$interface" 2>/dev/null || true)
        case "$address" in ''|127.*|169.254.*|0.*) continue ;; esac
        candidate_count=$((candidate_count + 1))
        candidate_address=$address
    done
    [ "$candidate_count" -eq 1 ] || die 'Cannot select a unique Wi-Fi IPv4 address. Use --listen-address with your LAN IP.'
    listen_address=$candidate_address
fi

valid_ipv4() {
    printf '%s\n' "$1" | awk -F. '
        NF != 4 {exit 1}
        {for (i=1; i<=4; i++) if ($i !~ /^[0-9]+$/ || length($i)>3 || $i+0>255) exit 1}'
}
valid_ipv4 "$listen_address" || die "Invalid IPv4 address: $listen_address"
case "$listen_address" in 127.*|169.254.*|0.*) die 'Use a LAN address, not a loopback/link-local address.' ;; esac
interface_data=$(ifconfig -a) || die 'Cannot read local interface addresses.'
interface=$(printf '%s\n' "$interface_data" | awk -v address="$listen_address" '
    /^[^ \t]/ {name=$1; sub(/:$/, "", name)}
    $1 == "inet" && $2 == address {print name}')
[ -n "$interface" ] || die "Address $listen_address is not assigned to this Mac."
interface_config=$(ifconfig "$interface") || die 'Cannot read the selected interface.'
netmask=$(printf '%s\n' "$interface_config" | awk -v address="$listen_address" '
    $1 == "inet" && $2 == address {for(i=3;i<NF;i++) if($i=="netmask") print $(i+1)}')

# macOS normally reports a hexadecimal mask. Accept dotted masks as well.
if [[ "$netmask" =~ ^0x[0-9a-fA-F]{8}$ ]]; then
    mask_value=$((netmask))
    netmask=$(printf '%d.%d.%d.%d' "$(((mask_value >> 24) & 255))" "$(((mask_value >> 16) & 255))" "$(((mask_value >> 8) & 255))" "$((mask_value & 255))")
fi
valid_ipv4 "$netmask" || die "Cannot determine the IPv4 subnet for $interface."
subnet=$(awk -v address="$listen_address" -v mask="$netmask" 'BEGIN {
    split(address,a,"."); split(mask,m,".")
    for(i=1;i<=4;i++) {
        value=0; bit=1; x=a[i]+0; y=m[i]+0
        for(j=0;j<8;j++) {if(x%2 && y%2) value+=bit; x=int(x/2); y=int(y/2); bit*=2}
        printf "%s%d", (i>1 ? "." : ""), value
    }
}')

forwards=$("$adb_bin" forward --list) || die 'Cannot list ADB forwarding.'
existing_forward=$(printf '%s\n' "$forwards" | awk -v port="tcp:$adb_port" '$2 == port')
if [ -n "$existing_forward" ]; then
    printf '%s\n' "$existing_forward" | awk -v serial="$device_serial" -v target="tcp:$device_port" '
        $1 != serial || $3 != target {bad=1} END {exit (bad || NR != 1)}' \
        || die "Port $adb_port already forwards to another ADB destination."
elif lsof -nP -iTCP:"$adb_port" -sTCP:LISTEN >/dev/null 2>&1; then
    die "Port $adb_port is occupied by an unrelated listener."
fi
if lsof -nP -iTCP:"$listen_port" -sTCP:LISTEN >/dev/null 2>&1; then
    die "Port $listen_port is already listening. Stop the earlier relay or conflicting application."
fi

cleanup() {
    result=$?
    trap - EXIT INT TERM HUP
    if [ -n "$relay_pid" ] && kill -STOP "$relay_pid" 2>/dev/null; then
        # Stop accepting before terminating children, so no relay survives Ctrl+C.
        pkill -TERM -P "$relay_pid" 2>/dev/null || true
        kill -TERM "$relay_pid" 2>/dev/null || true
        kill -CONT "$relay_pid" 2>/dev/null || true
        wait "$relay_pid" 2>/dev/null || true
    fi
    if [ "$created_forward" -eq 1 ]; then
        current=$("$adb_bin" forward --list 2>/dev/null || true)
        if printf '%s\n' "$current" | awk -v serial="$device_serial" -v port="tcp:$adb_port" -v target="tcp:$device_port" '
            $1==serial && $2==port && $3==target {found=1} END {exit !found}'; then
            "$adb_bin" -s "$device_serial" forward --remove "tcp:$adb_port" || true
        fi
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

if [ -z "$existing_forward" ]; then
    "$adb_bin" -s "$device_serial" forward --no-rebind "tcp:$adb_port" "tcp:$device_port" \
        || die 'Cannot create ADB forwarding.'
    created_forward=1
fi

check_websocket() {
    # A successful upgrade stays open; curl then times out with HTTP status 101.
    status=$(curl --noproxy '*' --http1.1 --max-time 2 --silent --output /dev/null --write-out '%{http_code}' \
        -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
        -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Sec-WebSocket-Version: 13' \
        "http://$1/control" 2>/dev/null || true)
    [ "$status" = '101' ]
}
check_websocket "127.0.0.1:$adb_port" || die 'The TV WebSocket server is not responding. Open MobPlayer TV in the selected emulator.'

"$socat_bin" -d -d "TCP4-LISTEN:$listen_port,bind=$listen_address,reuseaddr,fork,range=$subnet:$netmask" "TCP4:127.0.0.1:$adb_port" &
relay_pid=$!
ready=0
for attempt in 1 2 3; do
    if ! kill -0 "$relay_pid" 2>/dev/null; then die 'The relay failed to start; see socat output above.'; fi
    if check_websocket "$listen_address:$listen_port"; then ready=1; break; fi
    sleep 0.2
done
[ "$ready" -eq 1 ] || die 'The LAN relay is not responding. Check socat output and macOS Firewall.'

printf '\nReady: ws://%s:%s/control\n' "$listen_address" "$listen_port"
printf 'Device: %s | Interface: %s | Allowed subnet: %s:%s\n' "$device_serial" "$interface" "$subnet" "$netmask"
printf '%s\n' \
    "On your phone, use manual connection and enter the TV's pairing PIN." \
    'Keep this Terminal open. Press Ctrl+C to stop forwarding.' \
    'If prompted by macOS Firewall, allow incoming connections for socat.' \
    'Both devices must share the same LAN. Automatic discovery is not provided.'
wait "$relay_pid"
