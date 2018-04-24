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

    /* Find the servers that the script needs run against
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

    /* Read the PowerShell file for the workflow

    this_base.log("getting PS file")

    def ps_script = base.read_wf_file('sys-windows-update-ege-sql', 'ege-drop-and-recreate-assemblies.ps1')

    if (ps_script['response'] == 'error') {
        return ps_script
    }

    ps_script = ps_script['message']

    def get_dbs = this_base.read_wf_file('sys-windows-update-ege-sql', 'get-ege-databases.ps1')

    if (get_dbs['response'] == 'error') {
        return get_dbs
    }

    get_dbs = get_dbs['message']

    def dbas = []
    def successful_databases = []

    /* Run the PowerShell script
    /* Loop for servers */
    def list_of_ege_servers = ['delete', 'me']
    for (Integer i = 0; i < list_of_ege_servers.size(); i++) {
        /* this_base.log("getting the databases from '${list_of_ege_servers[i]}'")

        host_dbs = this_base.run_powershell(
            "Attempting to get the databases from the machine",
            get_dbs,
            this_base.get_cred_id(list_of_ege_servers[0]),
                [
                    '_address_': list_of_ege_servers[0],
                ]
        )

        if (host_dbs['response'] == 'error') {
            return host_dbs
        }

        dbas = host_dbs['message'].replace(' ', '').split('\r\n') */

        def creds = [[$class: 'UsernamePasswordMultiBinding', credentialsId: 'lower_region_databases', usernameVariable: '__lower_region_databases_username__', passwordVariable: '__lower_region_databases_password__']]

        String username = ''
        String password = ''
        node('!master && domain:core.cvent.org') {
            withCredentials(creds) {
                username = env['__lower_region_databases_username__']
                password = env['__lower_region_databases_password__']
            }
        }

        /* Loop for dbas on the ege */
        for (Integer j = 0; j < dbas.size(); j++) {
            recreate_assembly = this_base.run_powershell(
                "Attempting to drop and recreate assemblies on '${list_of_ege_servers[i]}'",
                ps_script,
                this_base.get_cred_id(list_of_ege_servers[i]),
                [
                    '_address_' : list_of_ege_servers[i],
                    '_database_' : dbas[j],
                    '__lower_region_databases_username__' : username,
                    '__lower_region_databases_password__' : password
                ]
            )
        } */

        /* successful_databases += list_of_ege_servers[i] */
    }
    /*
    if (successful_databases != list_of_ege_servers) {
        output['message'] = 'not all of the servers completed successfully'
    }

    output['response'] = 'ok'
    output['message'] = successful_databases
    */

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