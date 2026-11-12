; PurplePlatypus Windows installer — Inno Setup 6 (6.3 or later)
;
; Built by .github/workflows/package.yml, which first creates the jpackage app-image
; (PurplePlatypus\ at the repository root) and then compiles this script:
;
;   ISCC.exe /DAppVersion=1.14.0 /DArch=x64   packaging\PurplePlatypus.iss
;   ISCC.exe /DAppVersion=1.14.0 /DArch=arm64 packaging\PurplePlatypus.iss
;
; The installer is written to the repository root as PurplePlatypus-<version>.exe.
;
; Install scope (standard Inno Setup command-line switches):
;   (no switch)    Install for the current user only. No administrator rights needed.
;                  Default folder: %LOCALAPPDATA%\Programs\PurplePlatypus
;   /CURRENTUSER   Same as the default.
;   /ALLUSERS      Install for all users. Requires elevation (UAC prompt).
;                  Default folder: %ProgramFiles%\PurplePlatypus
;
; Other standard switches also work, e.g. /SILENT, /VERYSILENT, /SUPPRESSMSGBOXES,
; /NORESTART, /DIR="x:\path", /LOG="file", /MERGETASKS="!desktopicon".
; On upgrade with no scope switch, the previous install's scope is reused.

#ifndef AppVersion
  #error Define AppVersion on the ISCC command line, e.g. /DAppVersion=1.14.0
#endif

#ifndef Arch
  #define Arch "x64"
#endif

#if Arch == "arm64"
  #define ArchAllowed "arm64"
#elif Arch == "x64"
  ; x64compatible also allows installing the x64 build on Windows 11 on Arm.
  #define ArchAllowed "x64compatible"
#else
  #error Arch must be x64 or arm64
#endif

#define AppName      "PurplePlatypus"
#define AppExeName   "PurplePlatypus.exe"
#define AppPublisher "Glowing Cat"
#define AppImageDir  "..\PurplePlatypus"
#define ResourceDir  "..\app\src\main\resources"

[Setup]
; AppId identifies this application to Windows and to future installers/upgrades.
; NEVER change it once released.
AppId={{8283A591-2689-4FCD-A67F-87FF49754DD4}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL=https://github.com/richlesh/PurplePlatypus
AppSupportURL=https://github.com/richlesh/PurplePlatypus/issues
AppCopyright=(C) 2026 Glowing Cat
; {autopf} resolves to Program Files in all-users mode and to
; %LOCALAPPDATA%\Programs in current-user mode.
DefaultDirName={autopf}\{#AppName}
DisableProgramGroupPage=yes
; Default to a per-user install (no admin); allow /ALLUSERS or /CURRENTUSER to override.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=commandline
ArchitecturesAllowed={#ArchAllowed}
ArchitecturesInstallIn64BitMode={#ArchAllowed}
ChangesAssociations=yes
SetupIconFile={#ResourceDir}\app_icon.ico
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName}
OutputDir=..
OutputBaseFilename={#AppName}-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; The complete jpackage app-image: launcher, bundled runtime, app jar, demo and
; "AI Config Examples" folders.
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; Icons used by the file associations below.
Source: "{#ResourceDir}\doc_icon.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#ResourceDir}\textbundle_icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Registry]
; File associations. HKA writes to HKLM for all-users installs and HKCU for
; current-user installs. Each extension lists PurplePlatypus under OpenWithProgids
; ("Open with") and is set as the default handler, as the previous MSI did.
; Windows still honors any default the user has explicitly chosen.

; Markdown (.md, .markdown)
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.Markdown"; ValueType: string; ValueName: ""; ValueData: "Markdown Document"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.Markdown\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\doc_icon.ico,0"
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.Markdown\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\.md"; ValueType: string; ValueName: ""; ValueData: "PurplePlatypus.Markdown"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.md\OpenWithProgids"; ValueType: string; ValueName: "PurplePlatypus.Markdown"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.markdown"; ValueType: string; ValueName: ""; ValueData: "PurplePlatypus.Markdown"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.markdown\OpenWithProgids"; ValueType: string; ValueName: "PurplePlatypus.Markdown"; ValueData: ""; Flags: uninsdeletevalue

; TextBundle (.textbundle)
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.TextBundle"; ValueType: string; ValueName: ""; ValueData: "TextBundle Document"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.TextBundle\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\textbundle_icon.ico,0"
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.TextBundle\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\.textbundle"; ValueType: string; ValueName: ""; ValueData: "PurplePlatypus.TextBundle"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.textbundle\OpenWithProgids"; ValueType: string; ValueName: "PurplePlatypus.TextBundle"; ValueData: ""; Flags: uninsdeletevalue

; TextPack (.textpack)
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.TextPack"; ValueType: string; ValueName: ""; ValueData: "TextPack Document"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.TextPack\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\textbundle_icon.ico,0"
Root: HKA; Subkey: "Software\Classes\PurplePlatypus.TextPack\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\.textpack"; ValueType: string; ValueName: ""; ValueData: "PurplePlatypus.TextPack"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.textpack\OpenWithProgids"; ValueType: string; ValueName: "PurplePlatypus.TextPack"; ValueData: ""; Flags: uninsdeletevalue

[Code]
const
  UninstallRoot = 'Software\Microsoft\Windows\CurrentVersion\Uninstall';

{ Returns the ProductCode of a previous jpackage/WiX (MSI) PurplePlatypus install
  registered under RootKey, or '' if there is none. MSI products are registered
  under a subkey named after their ProductCode GUID with an msiexec UninstallString,
  which distinguishes them from this installer's own "<AppId>_is1" entry. }
function FindLegacyMsi(RootKey: Integer): String;
var
  SubKeys: TArrayOfString;
  I: Integer;
  Key, DisplayName, Publisher, UninstallString: String;
begin
  Result := '';
  if not RegGetSubkeyNames(RootKey, UninstallRoot, SubKeys) then
    Exit;
  for I := 0 to GetArrayLength(SubKeys) - 1 do
  begin
    Key := UninstallRoot + '\' + SubKeys[I];
    if RegQueryStringValue(RootKey, Key, 'DisplayName', DisplayName) then
      if DisplayName = '{#AppName}' then
        if RegQueryStringValue(RootKey, Key, 'Publisher', Publisher) then
          if Publisher = '{#AppPublisher}' then
            if RegQueryStringValue(RootKey, Key, 'UninstallString', UninstallString) then
              if Pos('msiexec', Lowercase(UninstallString)) > 0 then
              begin
                Result := SubKeys[I];
                Exit;
              end;
  end;
end;

{ Silently uninstalls a previous MSI-based install found under RootKey, so upgrading
  from the old installer does not leave two copies, two sets of shortcuts and
  conflicting file associations. Best effort: failures are logged, not fatal. }
procedure RemoveLegacyMsi(RootKey: Integer);
var
  ProductCode: String;
  ResultCode: Integer;
begin
  ProductCode := FindLegacyMsi(RootKey);
  if ProductCode = '' then
    Exit;
  Log('Removing previous MSI-based install: ' + ProductCode);
  if Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + ProductCode + ' /qn /norestart',
          '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    Log('msiexec exit code: ' + IntToStr(ResultCode))
  else
    Log('Could not start msiexec to remove ' + ProductCode);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
  begin
    { The previous installer was per-user (HKCU); also check machine-wide entries. }
    RemoveLegacyMsi(HKCU);
    RemoveLegacyMsi(HKLM32);
    if IsWin64 then
      RemoveLegacyMsi(HKLM64);
  end;
end;
