//go:build windows
// +build windows

package software

import (

	"golang.org/x/sys/windows/registry"
)

func scan() ([]SoftwareItem, error) {
	var apps []SoftwareItem
	seen := make(map[string]bool)

	keys := []struct {
		k    registry.Key
		path string
	}{
		{registry.LOCAL_MACHINE, `SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall`},
		{registry.LOCAL_MACHINE, `SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall`},
		{registry.CURRENT_USER, `SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall`},
		{registry.CURRENT_USER, `SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall`},
	}

	for _, kv := range keys {
		k, err := registry.OpenKey(kv.k, kv.path, registry.READ)
		if err != nil {
			continue
		}

		subKeyNames, err := k.ReadSubKeyNames(-1)
		k.Close()
		if err != nil {
			continue
		}

		for _, subKeyName := range subKeyNames {
			sk, err := registry.OpenKey(kv.k, kv.path+`\`+subKeyName, registry.READ)
			if err != nil {
				continue
			}

			displayName, _, err := sk.GetStringValue("DisplayName")
			if err != nil || displayName == "" {
				sk.Close()
				continue
			}

			// Some system components have ParentKeyName or SystemComponent=1
			systemComp, _, err := sk.GetIntegerValue("SystemComponent")
			if err == nil && systemComp == 1 {
				sk.Close()
				continue
			}

			parentKey, _, err := sk.GetStringValue("ParentKeyName")
			if err == nil && parentKey != "" {
				sk.Close()
				continue
			}

			// Deduplicate by name
			if seen[displayName] {
				sk.Close()
				continue
			}
			seen[displayName] = true

			displayVersion, _, _ := sk.GetStringValue("DisplayVersion")
			publisher, _, _ := sk.GetStringValue("Publisher")
			
			// We'll store the subkey name as ID
			id := subKeyName
			
			apps = append(apps, SoftwareItem{
				Name:    displayName,
				ID:      id,
				Version: displayVersion,
				Source:  publisher,
			})
			
			sk.Close()
		}
	}

	return apps, nil
}
