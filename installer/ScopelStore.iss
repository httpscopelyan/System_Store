; =============================================================
; Instalador completo do Scopel Store PDV (Inno Setup 6)
; =============================================================
; Instala o app (C:\ScopelStore), o PostgreSQL 16 como serviço do Windows (se ainda não existir),
; cria o banco db_scopel VAZIO (o Flyway monta as tabelas na 1ª abertura), grava o .env e cria o
; usuário inicial (GERENTE) no primeiro start do app.
;
; PRIVADO: não publique o .exe gerado (leva o instalador do PostgreSQL). Nenhuma senha mora neste
; arquivo: elas são digitadas na instalação (ou passadas por /DbPassword= /MasterPassword= etc.).
; Gerar:  .\scripts\build-installer.ps1 -Version 1.0.0 -PgInstaller <caminho do instalador do PostgreSQL 16>
; =============================================================

#ifndef AppVersion
  #error Defina /DAppVersion=X.Y.Z
#endif
#ifndef AppSourceDir
  #error Defina /DAppSourceDir=<pasta "Scopel Store" gerada pelo jpackage>
#endif
#ifndef PgInstaller
  #error Defina /DPgInstaller=<instalador do PostgreSQL 16>
#endif

[Setup]
AppId={{8B7C1D2E-4F3A-4C5B-9D6E-1A2B3C4D5E6F}
AppName=Scopel Store
AppVersion={#AppVersion}
AppPublisher=Scopel
DefaultDirName=C:\ScopelStore
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
OutputBaseFilename=ScopelStore-Instalador-{#AppVersion}
Compression=lzma2/fast
SolidCompression=no
WizardStyle=modern
UninstallDisplayIcon={app}\Scopel Store.exe

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Dirs]
; O atualizador roda como usuário comum e precisa trocar o JAR em {app}\app.
Name: "{app}"; Permissions: users-modify

[Files]
Source: "{#AppSourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion
Source: "{#PgInstaller}"; DestDir: "{tmp}"; DestName: "pg-setup.exe"; Flags: deleteafterinstall; Check: NeedsPostgres

[Icons]
Name: "{autodesktop}\Scopel Store"; Filename: "{app}\Scopel Store.exe"; WorkingDir: "{app}"
Name: "{autoprograms}\Scopel Store"; Filename: "{app}\Scopel Store.exe"; WorkingDir: "{app}"

[Run]
Filename: "{app}\Scopel Store.exe"; WorkingDir: "{app}"; Description: "Abrir o Scopel Store agora"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; O banco e o .env NÃO são removidos (dados da loja).
Type: filesandordirs; Name: "{app}\updates"
Type: filesandordirs; Name: "{app}\backup"

[Code]
var
  DbPage: TInputQueryWizardPage;
  UserPage: TInputQueryWizardPage;

function SetEnvironmentVariable(lpName, lpValue: String): Boolean;
  external 'SetEnvironmentVariableW@kernel32.dll stdcall';

function PgBaseDir: String;
var
  Base: String;
begin
  if RegQueryStringValue(HKLM64, 'SOFTWARE\PostgreSQL\Installations\postgresql-x64-16', 'Base Directory', Base) then
    Result := RemoveBackslash(Base)
  else
    Result := '';
end;

function NeedsPostgres: Boolean;
begin
  Result := PgBaseDir = '';
end;

{ Só ASCII visível, sem aspas nem barra invertida: os valores vão para linha de comando e para o .env. }
function IsSafe(const S: String; MaxLen: Integer; AllowSpace: Boolean): Boolean;
var
  I: Integer;
begin
  Result := (Length(S) > 0) and (Length(S) <= MaxLen);
  for I := 1 to Length(S) do
    if ((Ord(S[I]) < 33) and not ((Ord(S[I]) = 32) and AllowSpace)) or (Ord(S[I]) > 126)
       or (S[I] = '"') or (S[I] = '\') then
      Result := False;
end;

function ValidateInputs: String;
begin
  Result := '';
  if DbPage.Values[0] <> DbPage.Values[1] then
    Result := 'As senhas do banco não conferem.'
  else if not IsSafe(DbPage.Values[0], 64, False) then
    Result := 'Senha do banco inválida: use 1 a 64 caracteres ASCII, sem espaços, aspas ou barra invertida (\).'
  else if NeedsPostgres and (Length(DbPage.Values[0]) < 8) then
    Result := 'A senha do banco precisa ter pelo menos 8 caracteres.'
  else if not IsSafe(UserPage.Values[0], 50, False) then
    Result := 'Login inválido: use até 50 caracteres ASCII, sem espaços, aspas ou barra invertida.'
  else if not IsSafe(UserPage.Values[1], 30, True) then
    Result := 'Nome inválido: use até 30 caracteres ASCII, sem aspas ou barra invertida.'
  else if UserPage.Values[2] <> UserPage.Values[3] then
    Result := 'As senhas do usuário inicial não conferem.'
  else if (Length(UserPage.Values[2]) < 8) or not IsSafe(UserPage.Values[2], 64, False) then
    Result := 'A senha do usuário inicial precisa ter 8 a 64 caracteres ASCII, sem espaços, aspas ou barra invertida (\).';
end;

procedure InitializeWizard;
begin
  DbPage := CreateInputQueryPage(wpSelectDir, 'Banco de dados',
    'Senha do PostgreSQL (usuário "postgres")',
    'Defina a senha do banco. Se o PostgreSQL 16 já estiver instalado nesta máquina, informe a senha atual dele.');
  DbPage.Add('Senha do banco:', True);
  DbPage.Add('Repita a senha:', True);
  DbPage.Values[0] := ExpandConstant('{param:DbPassword|}');
  DbPage.Values[1] := DbPage.Values[0];

  UserPage := CreateInputQueryPage(DbPage.ID, 'Usuário inicial',
    'Primeiro acesso ao sistema',
    'Este usuário (perfil GERENTE) é criado na primeira abertura do sistema. Outros usuários são criados depois, dentro do sistema.');
  UserPage.Add('Login:', False);
  UserPage.Add('Nome:', False);
  UserPage.Add('Senha:', True);
  UserPage.Add('Repita a senha:', True);
  UserPage.Values[0] := ExpandConstant('{param:MasterLogin|}');
  UserPage.Values[1] := ExpandConstant('{param:MasterName|}');
  UserPage.Values[2] := ExpandConstant('{param:MasterPassword|}');
  UserPage.Values[3] := UserPage.Values[2];
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Msg: String;
begin
  Result := True;
  if (CurPageID = DbPage.ID) or (CurPageID = UserPage.ID) then
  begin
    Msg := ValidateInputs;
    { Cada página só reclama do que já foi preenchido por ela. }
    if (Msg <> '') and (((CurPageID = DbPage.ID) and (Pos('banco', Msg) > 0)) or
       ((CurPageID = UserPage.ID) and (Pos('banco', Msg) = 0))) then
    begin
      MsgBox(Msg, mbError, MB_OK);
      Result := False;
    end;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := ValidateInputs;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode, I: Integer;
  Pw, Base, Psql, SqlFile, EnvFile: String;
  Ok: Boolean;
begin
  if CurStep <> ssPostInstall then
    Exit;
  Pw := DbPage.Values[0];

  if NeedsPostgres then
  begin
    WizardForm.StatusLabel.Caption := 'Instalando o PostgreSQL 16 (pode levar alguns minutos)...';
    if not Exec(ExpandConstant('{tmp}\pg-setup.exe'),
        '--mode unattended --unattendedmodeui none --superpassword "' + Pw + '" --serverport 5432 ' +
        '--enable-components server,commandlinetools --disable-components pgAdmin,stackbuilder',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
      RaiseException('Falha ao instalar o PostgreSQL 16 (código ' + IntToStr(ResultCode) + ').');
  end;

  Base := PgBaseDir;
  if Base = '' then
    Base := ExpandConstant('{commonpf64}\PostgreSQL\16');
  Psql := Base + '\bin\psql.exe';

  WizardForm.StatusLabel.Caption := 'Criando o banco de dados...';
  SqlFile := ExpandConstant('{tmp}\criar-banco.sql');
  SaveStringToFile(SqlFile,
    'SELECT ''CREATE DATABASE db_scopel'' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = ''db_scopel'')'
    + #13#10 + '\gexec' + #13#10, False);
  SetEnvironmentVariable('PGPASSWORD', Pw);
  Ok := False;
  for I := 1 to 10 do
  begin
    if Exec(Psql, '-h 127.0.0.1 -p 5432 -U postgres -v ON_ERROR_STOP=1 -f "' + SqlFile + '"',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0) then
    begin
      Ok := True;
      Break;
    end;
    Sleep(3000);
  end;
  SetEnvironmentVariable('PGPASSWORD', '');
  if not Ok then
    RaiseException('Não foi possível criar o banco. Confira a senha do PostgreSQL e se o serviço está em execução.');

  { Reinstalação/atualização manual: nunca sobrescreve o .env existente. }
  EnvFile := ExpandConstant('{app}\.env');
  if not FileExists(EnvFile) then
    SaveStringToFile(EnvFile,
      'DB_NAME=db_scopel' + #13#10 +
      'DB_USER=postgres' + #13#10 +
      'DB_PASSWORD=' + Pw + #13#10 +
      'pdv.bootstrap.login=' + UserPage.Values[0] + #13#10 +
      'pdv.bootstrap.name=' + UserPage.Values[1] + #13#10 +
      'pdv.bootstrap.password=' + UserPage.Values[2] + #13#10, False);
end;
