On XPack Support (Security)
*****************************

X-Pack is the collection of extensions provided by elastic to enhance the capabilities of the Elastic Stack with things
such as reporting, monitoring and also security. If you installed x-pack your cluster will now be protected with the
security module, this will also be like this if you are using Elasticsearch through the Elastic Cloud solution.

=====================================================
Setup roles and users.
=====================================================

After installing the plugin, the first thing you will have to do is configure the necessary users and roles to access let
the LTR plugin operate.

We recommend you to create two separate roles, one for administrative task such as creating the models, updating the feature sets, etc ..
and one to run the queries.

For this configuration, we supose you already have identified, and created two users, one for running queries and one for doing administrative tasks. If you
need help to create the users, we recommend you to check the `x-pack api documentation for user management <https://www.elastic.co/guide/en/elasticsearch/reference/6.1/security-api-users.html>`_.

To create two roles, you can do it with these commands::

    POST /_xpack/security/role/ltr_admin
    {
      "cluster": [ "ltr" ],
      "indices": [
        {
            "names": [ ".ltrstore*" ],
            "privileges": [ "all" ],
        }
      ]
    }

    POST /_xpack/security/role/ltr_query
    {
        "cluster": [ "ltr" ],
        "indices": [
            {
                "names": [ ".ltrstore*" ],
                "privileges": [ "read" ],
            }
        ]
    }

the first one will allow the users to perform all the operations while the last one will only allow read operations.

Once the roles are defined, the last step will be to attach this roles to existing users, for this documentation we will suppose two users, ltr_admin and ltr_user. The commands to set the roles are::

    POST /_xpack/security/role_mapping/ltr_admins
    {
        "roles": [ "ltr_admin" ],
        "rules": {
            "field" : { "username" : [ "ltr_admin01", "ltr_admin02" ] }
        },
        "metadata" : {
            "version" : 1
        }
    }

    POST /_xpack/security/role_mapping/ltr_users
    {
        "roles": [ "ltr_query" ],
        "rules": {
            "field" : { "username" : [ "ltr_user01", "ltr_user02" ] }
        },
        "metadata" : {
            "version" : 1
        }
    }

After this two steps, your plugin will be fully functional in your x-pack protected cluster.

For more in deep information on how to define roles, we recommend you to check the elastic `x-pack api documentation <https://www.elastic.co/guide/en/x-pack/6.1/defining-roles.html>`_.


=====================================================
Considerations
=====================================================

The read access to models via the sltr query is not strictly gated by x-pack. The access will only be checked if the model needs
to be loaded, however If the model is already in the cache for that node no checks will be performed. This will generally not have
a major security impact, however is important to take into account in case is important for your use case.
