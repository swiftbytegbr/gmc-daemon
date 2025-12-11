#define MyAppName "GameManagerCloud Daemon"
#define MyAppVersion "2.2.1-EA"
#define MyAppPublisher "SwiftByte Kaspereit Faust GbR"
#define MyAppURL "https://gamemanager.cloud"
#define MyAppExeName "GmcDaemon.exe"

[Setup]
AppId={{2FB5C47B-766D-4601-8751-1EE19CECF86C}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={commonpf}\GameManagerCloud
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir={#SourcePath}\target\GmcDaemon\installer
OutputBaseFilename=gmc-daemon-setup
Compression=lzma
SolidCompression=yes
SetupIconFile={#SourcePath}\icon.ico
AllowRootDirectory=True
DisableWelcomePage=False
UsePreviousPrivileges=False
AppCopyright={#myAppPublisher}
LicenseFile={#SourcePath}\eula.txt
UninstallDisplayIcon={app}\GmcDaemon.exe

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "{#SourcePath}\target\GmcDaemon\GmcDaemon.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourcePath}\target\GmcDaemon\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs
Source: "{#SourcePath}\target\GmcDaemon\libs\*"; DestDir: "{app}\libs"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall runascurrentuser