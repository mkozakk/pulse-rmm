//go:build linux
// +build linux

package software

func osExecuteRemove(name, id string) (int32, string, error) {
	return executeAptCommand("remove", name)
}
