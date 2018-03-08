#!groovy

/* Created by: Anthony Gaetano */

def call(def base) {
    this_base = base
    def glob_objs = this_base.get_glob_objs()
    def output = [
        'response': 'error',
        'message': ''
    ]

    /* Validate and sanitize the input */
    /* def result = this.input_validation()

    if (result['response'] == 'error') {
        return input_validation
    }
    */

    def result = ''

    /* Find the servers that the script needs run against */
    def vcenters = ['mg20-vcsa1-001.core.cvent.org']
    def list_of_vms = ''

    for (Integer i = 0; i < vcenters.size(); i++) {
        result = this_base.run_vmwarecli(
            'Getting list of all virtual machines in vCenter',
            "(get-vm).name -split '`n' | %{\$_.trim()}",
            vcenters[i],
            [:]
        )

        if (result['response'] == 'error') {
            return result
        }

        if (i == 0) {
            list_of_vms = result['message'].split('\r\n')
        } else {
            list_of_vms += result['message'].split('\r\n')
        }
    }

    list_of_ege_servers = []

    for (Integer i = 0; i < list_of_vms.size(); i++) {
        if (list_of_vms[i].contains(wf_region + '-ege')) {
            list_of_ege_servers += list_of_vms[i]
        }
    }

    list_of_ege_servers = list_of_ege_servers.reverse()


    /* Read the PowerSheel file for the workflow */

    // list_of_ege_servers = ['ap20-ege-101']
    this_base.log("getting SQL file")

    def sql_script = base.read_wf_file('sys-windows-update-ege-sql', 'ege-drop-and-recreate-assemblies.sql')

    if(sql_script['response'] == 'error'){
        return sql_script
    }

    this_base.log("getting PS file")

    def ps_script = base.read_wf_file('sys-windows-update-ege-sql', 'ege-drop-and-recreate-assemblies.ps1')

    if(ps_script['response'] == 'error'){
        return ps_script
    }

    ps_script = ps_script['message']
    sql_script = sql_script['message']
    sleep_time = 60

    /* Run the PowerShell script */
    for (Integer i = 0; i < list_of_ege_servers.size(); i++) {
        output = this_base.run_powershell(
            "Attempting to drop and recreate assumblies on ${list_of_ege_servers[i]}",
            ps_script,
            base.get_cred_id(list_of_ege_servers[i]),
            [
                '_address_': list_of_ege_servers[i],
                '_sql_': sql_script
            ]
        )
    }

    return output
}
/*
def input_validation() {
    def output = [
        'response': 'error',
        'message': ''
    ]

    if("${wf_region"}" == ''){
        output['message'] = 'Missing required parameter'

        return output
    }

    return output
}
*/
return this
