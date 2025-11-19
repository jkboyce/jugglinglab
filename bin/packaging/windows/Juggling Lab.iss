;This file will be executed next to the application bundle image
;I.e. current directory will contain folder Juggling Lab with application files

#define MyAppName "Juggling Lab"
#define MyOutputFileName "JugglingLab"
#define MyAppVersion "1.6.7"
#define MyAppYear "2025"
#define MyAppExeName "Juggling Lab.exe"
#define MyAppIconsName "Juggling Lab.ico"
#define MyAppDocIconsName "JML_document.ico"
#define MyWizardImageFileName "Juggling Lab-setup-icon.bmp"

[Setup]
SourceDir=.
AppId={{JugglingLab}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher=Jack Boyce
;AppComments={#MyAppName}
AppCopyright=Copyright (C) 2002-{#MyAppYear} Jack Boyce and the Juggling Lab contributors
ChangesAssociations=yes
;First option installs per-user, second system-wide
;DefaultDirName={localappdata}\{#MyAppName}
DefaultDirName={commonpf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableStartupPrompt=No
DisableDirPage=Yes
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=No
;Optional License
LicenseFile=
;require Windows 10 or above
MinVersion=10.0
OutputBaseFilename={#MyOutputFileName}-{#MyAppVersion}
Compression=lzma
SolidCompression=yes
;First line is for per-user installation, second is system-wide:
;PrivilegesRequired=lowest
PrivilegesRequired=admin
SetupIconFile=Juggling Lab\{#MyAppIconsName}
UninstallDisplayIcon={app}\{#MyAppIconsName}
UninstallDisplayName={#MyAppName}
WizardImageStretch=No
WizardSmallImageFile={#MyWizardImageFileName}
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "Juggling Lab\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "Juggling Lab\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppIconsName}"; Check: returnTrue()
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}";  IconFilename: "{app}\{#MyAppIconsName}"; Check: returnFalse()

[Run]
Filename: "{app}\{#MyAppExeName}"; Parameters: "-Xappcds:generatecache"; Check: returnFalse()
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,Juggling Lab}"; Flags: nowait postinstall skipifsilent; Check: returnTrue()
Filename: "{app}\{#MyAppExeName}"; Parameters: "-install -svcName ""Juggling Lab"" -svcDesc ""Juggling Lab"" -mainExe ""{#MyAppExeName}""  "; Check: returnFalse()

[Registry]
Root: HKCR; Subkey: ".jml\OpenWithProgIDs"; ValueType: string; ValueName: "JugglingLab.jml"; ValueData: ""; Flags: uninsdeletevalue 
Root: HKCR; Subkey: "JugglingLab.jml"; ValueType: string; ValueName: ""; ValueData: "Juggling Lab JML File"; Flags: uninsdeletekey
Root: HKCR; Subkey: "JugglingLab.jml\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppDocIconsName}"
Root: HKCR; Subkey: "JugglingLab.jml\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" open ""%1"""
Root: HKCR; Subkey: "Applications\{#MyAppExeName}\SupportedTypes"; ValueType: string; ValueName: ".jml"; ValueData: ""

[UninstallRun]
Filename: "{app}\{#MyAppExeName} "; Parameters: "-uninstall -svcName Juggling Lab -stopOnUninstall"; Check: returnFalse()

[Code]
function returnTrue(): Boolean;
begin
  Result := True;
end;

function returnFalse(): Boolean;
begin
  Result := False;
end;

function InitializeSetup(): Boolean;
begin
// Possible future improvements:
//   if version less or same => just launch app
//   if upgrade => check if same app is running and wait for it to exit
//   Add pack200/unpack200 support?
  Result := True;
end;
