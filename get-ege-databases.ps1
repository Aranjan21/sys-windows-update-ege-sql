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
    # Adding in the SQL Snapins so we can leverage Invoke-Sqlcmd
    Add-PSSnapin SqlServerCmdletSnapin100
    Add-PSSnapin SqlServerProviderSnapin100

    `$query_enum = "SET NOCOUNT ON;select name from sys.databases where name like 'EGE_TARGET%'"
    `$dbs = sqlcmd -U cvent -P n0rth -S "localhost,50000" -Q `$query_enum -h -1

    Write-Output `$dbs

"@)

# Execute the script on the remote Windows machine
Invoke-Command -Session $ps_session -ScriptBlock $remote

# IMPORTANT: Remove the PowerShell session so concurrent shells on the remote machine do not exceed the limit
Remove-PSSession -Session $ps_session | Out-Null