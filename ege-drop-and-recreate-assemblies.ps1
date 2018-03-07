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
# Therefore, some (not all) of those characters require escaping with `

$remote = [scriptblock]::Create(@"
    `$status = (Get-Service `"$env:_service_`" | select -ExpandProperty status)

    if(`$status -Match `"Stopped`"){
        switch (`"$env:_choice_`")
            {
                `"Start`" {Start-Service `"$env:_service_`"; Write-Host `"$env:_service_ has been started`"}
                `"Stop`" {Write-Host `"$env:_service_ is not running and cannot be stopped again`"}
				`"Restart`" {Restart-Service `"$env:_service_`"; Write-Host `"$env:_service_ was stopped and has been started`"}
                `"Status`" {Write-Host `"$env:_service_ is `$status`"}
            }
	}
    if(`$status -Match `"Running`"){
        switch (`"$env:_choice_`")
            {
                `"Start`" {Write-Host `"$env:_service_ is already running and cannot be started again`"}
                `"Stop`" {Stop-Service `"$env:_service_`"; Write-Host `"$env:_service_ has been stopped`"}
				`"Restart`" {Restart-Service `"$env:_service_`"; Write-Host `"$env:_service_ has been restarted`"}
                `"Status`" {Write-Host `"$env:_service_ is `$status`"}
            }
    }
	if(`$status -Match `"Waiting`"){
		`"$env:_service_ is in Waiting status. Please login and check the processes related to the service. They may need manually terminated`"
	}
"@)

# Execute the script on the remote Windows machine
Invoke-Command -Session $ps_session -ScriptBlock $remote

# IMPORTANT: Remove the PowerShell session so concurrent shells on the remote machine do not exceed the limit
Remove-PSSession -Session $ps_session | Out-Null