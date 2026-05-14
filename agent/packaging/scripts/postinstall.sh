#!/bin/sh
set -e
# Only register the service if the config file is already in place.
# The install script (Phase 6) writes the config before installing the package,
# so this covers the one-liner flow.  A bare dpkg/rpm install without config
# silently skips service registration — the admin can run
# `pulse-agent service install` manually after writing the config file.
if [ -f /etc/pulse-agent/config.yaml ]; then
    /usr/local/bin/pulse-agent service install || true
fi
