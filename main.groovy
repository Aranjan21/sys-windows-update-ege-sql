#!groovy

/* Created by: Anthony Gaetano */

def call(def base) {
    this_base = base
    def glob_objs = base.get_glob_objs()
    def output = [
        'response': 'error',
        'message': ''
    ]

    def result = ''

    /* Find the servers that the script needs run against */
    /* def vcenters = ['mg20-vcsa1-001.core.cvent.org', 'mg11-vcsa1-001.core.cvent.org', 'mg01-vcsa1-001.core.cvent.org', 'mg01-vcsa1-011.core.cvent.org'] */
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

    /* Read the PowerShell files for the workflow */
    this_base.log('getting PS file')

    def ps_script = this_base.read_wf_file('sys-windows-update-ege-sql', 'ege-drop-and-recreate-assemblies.ps1')

    if (ps_script['response'] == 'error') {
        return ps_script
    }

    ps_script = ps_script['message']

    def get_dbs = this_base.read_wf_file('sys-windows-update-ege-sql', 'get-ege-databases.ps1')

    if (get_dbs['response'] == 'error') {
        return get_dbs
    }

    get_dbs = get_dbs['message']

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

    /* Create the change ticket */
    def chg_desc = 'Dropping and Recreating Assemblies'
    def chg_ticket = this_base.create_chg_ticket(
        list_of_ege_servers[0],
        "Drop and Recreate Assemblies on ${wf_region} EGE servers",
        chg_desc,
        '',
        wf_requester
    )

    if (chg_ticket['response'] == 'error') {
        output['message'] = "FAILURE: the assemblies were not rebuilt because the change ticket was not created successfully: ${chg_ticket['message']}"
        return output
    }

    def dbas = []
    def successful_databases = []
    def steps = [:]

    /* Loop through each assembly on each ege database */
    node('!master && os:windows && domain:core.cvent.org') {
        withCredentials(creds) {
            /* Run the PowerShell scripts */
            /* Loop for servers */
            for (Integer i = 0; i < list_of_ege_servers.size(); i++) {

                /* Create a DataDog event for the activity */
                steps = this.create_tlsint_req(
                    [
                        'method': 'datadog_create_event',
                        'params':
                        [
                            'title': "Recreating database assemblies for ${wf_region} region",
                            'text': 'Post patching activity for EGE servers requires the NOC to drop and rebuild the database assemblies for each EGE host within the patched region',
                            'priority': 'normal',
                            'host': "${list_of_ege_servers[i]}",
                            'alert_type': 'info'
                        ]
                    ]
                )

                this_base.log("getting the databases from '${list_of_ege_servers[i]}'")

                host_dbs = this_base.run_powershell(
                    'Attempting to get the databases from the machine',
                    get_dbs,
                    this_base.get_cred_id(list_of_ege_servers[i]),
                        [
                            '_address_': list_of_ege_servers[i]
                        ]
                )
                /* Parse and split the result into an array */
                dbas = host_dbs['message'].replace(' ', '').split('\r\n')

                /* Update the change ticket with the databases that will be rebuilt */
                this_base.update_chg_ticket_desc("The following assemblies will be rebuilt on ${list_of_ege_servers[i]}: ${dbas}")

                if (host_dbs['response'] == 'error') {
                    return host_dbs
                }

                def time = ''

                /* Loop for dbas on the ege */
                for (Integer j = 0; j < dbas.size(); j++) {

                    recreate_assembly = this_base.run_powershell(
                        "Attempting to drop and recreate '${dbas[j]}' on '${list_of_ege_servers[i]}'",
                        ps_script,
                        this_base.get_cred_id(list_of_ege_servers[i]),
                        [
                            '_address_': list_of_ege_servers[i],
                            '_database_': dbas[j]
                        ]
                    )

                    if (recreate_assembly['response'] == 'error') {
                        output['message'] = "FAILURE: ${dbas[j]} failed to successfully drop and rebuild"
                        this_base.update_chg_ticket_desc(output['message'])
                        this_base.close_chg_ticket(false)
                        return output
                    }

                    /* Update the change ticket with the databases that was be rebuilt */
                    time = this_base.get_time()
                    this_base.update_chg_ticket_desc("${time} - ${dbas[j]} was rebuilt successful")
                }

                /* Update and close the change ticket after all assemblies have been rebuilt */
                this_base.update_chg_ticket_desc("SUCCESS: All assembly rebuilds have completed successfully on ${list_of_ege_servers[i]}")
                this_base.close_chg_ticket(true)

                successful_databases += list_of_ege_servers[i]
            }
        }
    }
    /* Verify that all of the tested servers were successful */
    if (successful_databases != list_of_ege_servers) {
        output['message'] = 'Either all of the databases did not complete successfully or one of them was skipped. View the change ticket or Jenkins console to see which nodes the script ran against.'
        return output
    }

    output['response'] = 'ok'
    output['message'] = "The following hosts successfully had their databases rebuilt: ${successful_databases}"

    return output
}

return this
