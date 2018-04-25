# sys-windows-update-ege-sql
# Created by: Anthony Gaetano

# This script will run on a Jenkins Windows slave

# Setup credentials
$sec_passwd = ConvertTo-SecureString $env:__password__ -AsPlainText -Force
$my_creds = New-Object System.Management.Automation.PSCredential($env:__username__, $sec_passwd)

# Open PowerShell Session to the remote Windows machine
# Use the '-ErrorAction Stop' option so that the script ends here if the session fails to open
$ps_session = New-PSSession -ComputerName $env:_address_ -Credential $my_creds -ErrorAction Stop

# When building the script that will execute on the remote Windows machine,
# be mindful that special characters such as $ will be expanded by the Windows slave
# Therefore, some (not all) of those characters require escaping with

$remote = [scriptblock]::Create(@"
    Write-Output "we are inside the powershell script now!"
    # Adding in the SQL Snapins so we can leverage Invoke-Sqlcmd
    Add-PSSnapin SqlServerCmdletSnapin100
    Add-PSSnapin SqlServerProviderSnapin100

    `$query = [IO.File]::ReadAllText("D:\sqltest\EGE_drop_and_recreate_assemblies.sql")

    Write-Output "Starting Script"

    # The sql script generates a bunch of warnings, so we're suppressing them with the below setting
    `$WarningPreference = "silentlyContinue"
    try {
        `$newquery = `$query -replace "USE \[EGE_TARGET\]","USE [$env:_database_]"
        Write-Output "Starting Database: $env:_database_"
        Invoke-Sqlcmd -Username $env:__database_username__ -Password $env:__database_password__ -Query `$newquery -ServerInstance "localhost,50000" -ConnectionTimeout 30 -QueryTimeout 90
    } catch {
        `$errormessage = "ERROR: `$_"
        Write-Error `$errormessage
    }

    Write-Output "Completed Database: $env:_database_"
"@)

# Execute the script on the remote Windows machine
Invoke-Command -Session $ps_session -ScriptBlock $remote

# IMPORTANT: Remove the PowerShell session so concurrent shells on the remote machine do not exceed the limit
Remove-PSSession -Session $ps_session | Out-Null