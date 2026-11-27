; Pocket Agent for Windows — NSIS installer (Windows 7-11, per-user, no admin)
; Build:  makensis /DVERSION=v6.1.0 /DFULLVERSION=6.1.0.0 installer.nsi
; Expects PocketAgent.exe next to this script.

!ifndef VERSION
  !define VERSION "v0.0.0"
!endif
!ifndef FULLVERSION
  !define FULLVERSION "0.0.0.0"
!endif

!include "MUI2.nsh"

Name "Pocket Agent"
OutFile "PocketAgent-Setup-${VERSION}.exe"
InstallDir "$LOCALAPPDATA\Programs\PocketAgent"
InstallDirRegKey HKCU "Software\PocketAgent" "InstallDir"
RequestExecutionLevel user
Unicode true

VIProductVersion "${FULLVERSION}"
VIAddVersionKey "ProductName" "Pocket Agent"
VIAddVersionKey "FileDescription" "Pocket Agent for Windows - AI agent"
VIAddVersionKey "FileVersion" "${FULLVERSION}"
VIAddVersionKey "LegalCopyright" "AnishtayiN"

!define MUI_ABORTWARNING
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Install"
  SetOutPath "$INSTDIR"
  File "PocketAgent.exe"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\PocketAgent" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketAgent" \
    "DisplayName" "Pocket Agent"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketAgent" \
    "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketAgent" \
    "DisplayIcon" "$INSTDIR\PocketAgent.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketAgent" \
    "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketAgent" \
    "Publisher" "AnishtayiN"
  CreateDirectory "$SMPROGRAMS\Pocket Agent"
  CreateShortcut "$SMPROGRAMS\Pocket Agent\Pocket Agent.lnk" "$INSTDIR\PocketAgent.exe"
  CreateShortcut "$SMPROGRAMS\Pocket Agent\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortcut "$DESKTOP\Pocket Agent.lnk" "$INSTDIR\PocketAgent.exe"
SectionEnd

Section "Uninstall"
  Delete "$INSTDIR\PocketAgent.exe"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  Delete "$SMPROGRAMS\Pocket Agent\Pocket Agent.lnk"
  Delete "$SMPROGRAMS\Pocket Agent\Uninstall.lnk"
  RMDir "$SMPROGRAMS\Pocket Agent"
  Delete "$DESKTOP\Pocket Agent.lnk"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\PocketAgent"
  DeleteRegKey HKCU "Software\PocketAgent"
SectionEnd
