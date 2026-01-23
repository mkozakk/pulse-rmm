# Windows shell session — running tests

The Windows session tests (`session_windows_test.go`) require a Windows 10+ machine with ConPTY support (build 1903+). CI runs Linux only, so these must be run manually on a Windows VM or physical machine.

```powershell
# from the agent directory
go test ./internal/shell/... -v -run TestSession
```

PowerShell must be on `PATH` (it is by default on Windows 10+). The tests start a shell, send a command over the PTY, and assert on the output — expect each test to complete within 3 seconds.
