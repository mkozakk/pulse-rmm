; Pulse RMM Agent Windows Installer
; Requires NSIS 3.x — build with: makensis installer.nsi
; The binary is expected at: ..\..\dist\pulse-agent-windows-amd64.exe

!define APP_NAME "Pulse RMM Agent"
!define APP_EXE  "pulse-agent.exe"
!define INSTALL_DIR "$PROGRAMFILES64\PulseAgent"
!define DATA_DIR    "$APPDATA\pulse-agent"
!define SERVICE_NAME "PulseAgent"

Name "${APP_NAME}"
OutFile "..\..\dist\pulse-agent-installer.exe"
InstallDir "${INSTALL_DIR}"
RequestExecutionLevel admin

; Prompt for API URL and token if not passed on the command line.
; Silent install: makensis /DAPI_URL=https://... /DTOKEN=<uuid> installer.nsi
Var ApiUrl
Var Token

Page custom ApiPage ApiPageLeave
Page instfiles

Function ApiPage
    !ifndef API_URL
        nsDialogs::Create 1018
        Pop $0

        ${NSD_CreateLabel} 0 0 100% 12u "API URL (e.g. https://pulse.example.com):"
        Pop $0
        ${NSD_CreateText} 0 14u 100% 12u ""
        Pop $ApiUrl

        ${NSD_CreateLabel} 0 32u 100% 12u "Enrolment token:"
        Pop $0
        ${NSD_CreateText} 0 46u 100% 12u ""
        Pop $Token

        nsDialogs::Show
    !endif
FunctionEnd

Function ApiPageLeave
    !ifndef API_URL
        ${NSD_GetText} $ApiUrl $ApiUrl
        ${NSD_GetText} $Token  $Token
    !else
        StrCpy $ApiUrl "${API_URL}"
        StrCpy $Token  "${TOKEN}"
    !endif
FunctionEnd

Section "Install"
    SetOutPath "${INSTALL_DIR}"
    File "..\..\dist\pulse-agent-windows-amd64.exe"
    Rename "${INSTALL_DIR}\pulse-agent-windows-amd64.exe" "${INSTALL_DIR}\${APP_EXE}"

    ; Write config file
    CreateDirectory "${DATA_DIR}"
    FileOpen $0 "${DATA_DIR}\config.yaml" w
    FileWrite $0 "api_url: $ApiUrl$\r$\n"
    FileWrite $0 "enrolment_token: $Token$\r$\n"
    FileWrite $0 "data_dir: ${DATA_DIR}$\r$\n"
    FileWrite $0 "log_level: info$\r$\n"
    FileClose $0

    ; Register and start the service
    ExecWait '"${INSTALL_DIR}\${APP_EXE}" service install'
    ExecWait '"${INSTALL_DIR}\${APP_EXE}" run'

    ; Write uninstaller
    WriteUninstaller "${INSTALL_DIR}\uninstall.exe"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\PulseAgent" \
        "DisplayName" "${APP_NAME}"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\PulseAgent" \
        "UninstallString" "${INSTALL_DIR}\uninstall.exe"
SectionEnd

Section "Uninstall"
    ExecWait '"${INSTALL_DIR}\${APP_EXE}" service uninstall'
    Delete "${INSTALL_DIR}\${APP_EXE}"
    Delete "${INSTALL_DIR}\uninstall.exe"
    RMDir  "${INSTALL_DIR}"
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\PulseAgent"
    ; Intentionally leave DATA_DIR so endpoint identity survives reinstall.
SectionEnd
