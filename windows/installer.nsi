; Aura for Windows — NSIS installer (Windows 7-11, per-user, no admin)
; Build:  makensis /DVERSION=v6.1.0 /DFULLVERSION=6.1.0.0 installer.nsi
; Expects Aura.exe next to this script.

!ifndef VERSION
  !define VERSION "v0.0.0"
!endif
!ifndef FULLVERSION
  !define FULLVERSION "0.0.0.0"
!endif

!include "MUI2.nsh"

Name "Aura"
OutFile "Aura-Setup-${VERSION}.exe"
InstallDir "$LOCALAPPDATA\Programs\Aura"
InstallDirRegKey HKCU "Software\Aura" "InstallDir"
RequestExecutionLevel user
Unicode true

VIProductVersion "${FULLVERSION}"
VIAddVersionKey "ProductName" "Aura"
VIAddVersionKey "FileDescription" "Aura for Windows - AI agent"
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
  File "Aura.exe"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Aura" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Aura" \
    "DisplayName" "Aura"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Aura" \
    "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Aura" \
    "DisplayIcon" "$INSTDIR\Aura.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Aura" \
    "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Aura" \
    "Publisher" "AnishtayiN"
  CreateDirectory "$SMPROGRAMS\Aura"
  CreateShortcut "$SMPROGRAMS\Aura\Aura.lnk" "$INSTDIR\Aura.exe"
  CreateShortcut "$SMPROGRAMS\Aura\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortcut "$DESKTOP\Aura.lnk" "$INSTDIR\Aura.exe"
SectionEnd

Section "Uninstall"
  Delete "$INSTDIR\Aura.exe"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  Delete "$SMPROGRAMS\Aura\Aura.lnk"
  Delete "$SMPROGRAMS\Aura\Uninstall.lnk"
  RMDir "$SMPROGRAMS\Aura"
  Delete "$DESKTOP\Aura.lnk"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Aura"
  DeleteRegKey HKCU "Software\Aura"
SectionEnd
