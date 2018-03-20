# sys-windows-update-ege-sql  
  
Created by: Anthony Gaetano AGaetano@cvent.com  
  
## Description  
The necessity of this job comes from Windows patching consistently breaking SQL on the EGE nodes. See the Jira ticket below for more details:
https://jira.cvent.com/browse/ITNOC-167  
  
## CLI Example  
**To run the SQL against a specific region of EGE servers:**  
`knife jenkins create sys-windows-update-ege-sql --instance cloudops-jenkins --params '{"wf_ticket_number":"CHG000001", "wf_region":"ap20"}'`  
