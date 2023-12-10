#define MyAppName "GameManagerCloud Daemon"
#define MyAppVersion "1.0"
#define MyAppPublisher "SwiftByte Kaspereit Faust Steurer GbR"
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
DefaultDirName={pf}\GameManagerCloud
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
LicenseFile=C:\Users\Lion Kaspereit\IdeaProjects\gmc-daemon\eula.txt

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "{#SourcePath}\target\GmcDaemon\GmcDaemon.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourcePath}\target\GmcDaemon\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs
Source: "{#SourcePath}\target\GmcDaemon\libs\*"; DestDir: "{app}\libs"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent runascurrentuser
