#!/bin/sh
set -e

# Ensure the uinput kernel module is loaded now and on every boot — Debian
# doesn't auto-load it. The desktop helper needs /dev/uinput to inject input
# under Wayland.
modprobe uinput 2>/dev/null || true
if [ -d /etc/modules-load.d ]; then
    echo uinput > /etc/modules-load.d/pulse-agent-uinput.conf
fi

# Reload udev so the uinput rule takes effect immediately (no reboot needed
# for the desktop helper to inject input on Wayland sessions).
if command -v udevadm >/dev/null 2>&1; then
    udevadm control --reload-rules || true
    udevadm trigger /dev/uinput || true
fi

# Add real human users (uid >= 1000, < 65534) to the 'input' group so the
# Wayland uinput path works. The helper runs as the logged-in user.
if command -v getent >/dev/null 2>&1 && command -v usermod >/dev/null 2>&1; then
    if getent group input >/dev/null 2>&1; then
        getent passwd | awk -F: '$3 >= 1000 && $3 < 65534 {print $1}' | while read -r u; do
            usermod -aG input "$u" || true
        done
    fi
fi

# Only register the service if the config file is already in place.
# The install script writes the config before installing the package, so this
# covers the one-liner flow. A bare dpkg/rpm install without config skips
# service registration — the admin can run `pulse-agent service install`
# manually after writing the config file.
if [ ! -f /etc/pulse-agent/config.yaml ]; then
    echo "pulse-agent: no config at /etc/pulse-agent/config.yaml — skipping service registration"
    exit 0
fi

if ! /usr/local/bin/pulse-agent service install; then
    echo "pulse-agent: 'service install' failed" >&2
    exit 1
fi

if command -v systemctl >/dev/null 2>&1; then
    systemctl daemon-reload || true
    systemctl enable pulse-agent.service || true
    systemctl restart pulse-agent.service || {
        echo "pulse-agent: failed to start service — check 'journalctl -u pulse-agent'" >&2
        exit 1
    }
    echo "pulse-agent: service started"
fi
