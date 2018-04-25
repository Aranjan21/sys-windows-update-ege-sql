#!groovy

/* Created by: Anthony Gaetano */

def call(def base) {
    this_base = base
    def glob_objs = this_base.get_glob_objs()
    def output = [
        'response': 'error',
        'message': ''
    ]

    /* Validate and sanitize the input
    def result = this.input_validation()

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

    Read the PowerShell file for the workflow */

    list_of_ege_servers = ['ap20-ege-101']

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
    def creds = ''

    /* get the database creds */
    if (wf_region == 'ap20') {
        creds = [[$class: 'UsernamePasswordMultiBinding', credentialsId: 'ap20_database', usernameVariable: '__database_username__', passwordVariable: '__database_password__']]
    } else if (wf_region == 'sg20' || wf_region == 'ld01') {
        creds = [[$class: 'UsernamePasswordMultiBinding', credentialsId: 'ld01_database', usernameVariable: '__database_username__', passwordVariable: '__database_password__']]
    } else if (wf_region == 'ts20') {
        creds = [[$class: 'UsernamePasswordMultiBinding', credentialsId: 'ts20_database', usernameVariable: '__database_username__', passwordVariable: '__database_password__']]
    } else if (wf_region == 'ct50') {
        creds = [[$class: 'UsernamePasswordMultiBinding', credentialsId: 'ct50_database', usernameVariable: '__database_username__', passwordVariable: '__database_password__']]
    } else if (wf_region == 'pr01' || wf_region == 'pr11') {
        creds = [[$class: 'UsernamePasswordMultiBinding', credentialsId: 'p2_databse', usernameVariable: '__database_username__', passwordVariable: '__database_password__']]
    }

    node('!master && os:windows && domain:core.cvent.org') {
        withCredentials(creds) {
            /* Run the PowerShell script */
            /* Loop for servers */
            for (Integer i = 0; i < list_of_ege_servers.size(); i++) {

                /* Create the change ticket */
                def chg_desc = "Dropping and Recreating Assemblies on ${list_of_ege_servers[i]}"
                def cheg_ticket = base.create_chg_ticket(
                    list_of_ege_servers[i],
                    "Drop and Recreate Assemblies",
                    chg_desc,
                    '',
                    wf_requester
                )

                if (cheg_ticket['response'] == 'error') {
                    output['message'] = "FAILURE: the assemblies were not rebuilt on ${list_of_ege_servers[i]} beacuse the change ticket was not created successfully: ${chg_ticket['message']}"
                    return output
                }

                this_base.log("getting the databases from '${list_of_ege_servers[i]}'")

                host_dbs = this_base.run_powershell(
                    "Attempting to get the databases from the machine",
                    get_dbs,
                    this_base.get_cred_id(list_of_ege_servers[i]),
                        [
                            '_address_' : list_of_ege_servers[i]
                        ]
                )

                dbas = host_dbs['message'].replace(' ', '').split('\r\n')

                /* Update the change ticket with the databases that will be rebuilt */
                base.update_chg_ticket_desc(dbas)

                if (host_dbs['response'] == 'error') {
                    return host_dbs
                }

                /* Loop for dbas on the ege */
                for (Integer j = 0; j < dbas.size(); j++) {
                    recreate_assembly = this_base.run_powershell(
                        "Attempting to drop and recreate '${dbas[j]}' on '${list_of_ege_servers[i]}'",
                        ps_script,
                        this_base.get_cred_id(list_of_ege_servers[i]),
                        [
                            '_address_' : list_of_ege_servers[i],
                            '_database_' : dbas[j]
                        ]
                    )

                    if (recreate_assembly['response'] == 'error') {
                        output['message'] = "FAILURE: ${dbas[j]} failed to successfully drop and rebuild"
                        base.update_chg_ticket_desc(output['message'])
                        return output
                    }

                    /* Update the change ticket with the databases that was be rebuilt */
                    base.update_chg_ticket_desc(recreate_assembly['message'])
                }

                /* Update and close the change ticket after all assemblies have been rebuilt */
                base.update_chg_ticket_desc("Completed rebuilds on ${list_of_ege_servers[i]}")
                base.close_chg_ticket(true)

                successful_databases += list_of_ege_servers[i]
            }
        }
    }

    if (successful_databases != list_of_ege_servers) {
        output['message'] = 'not all of the servers completed successfully'
    }

    output['response'] = 'ok'
    output['message'] = host_dbs


    return output
}

/* def input_validation() {
    def output = [
        'response': 'error',
        'message': ''
    ]

    if("${wf_region"}" == ''){
        output['message'] = 'Missing required parameter'

        return output
    }

    return output
} */

return this