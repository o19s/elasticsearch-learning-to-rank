from elasticsearch_xpack import XPackClient
import getpass
import sys
from utils import Elasticsearch

if __name__ == "__main__":
    if len(sys.argv) == 2:
        password = getpass.getpass()
    elif len(sys.argv) == 3:
        password = sys.argv[2]
    else:
        sys.exit(-1)

    username = sys.argv[1]

    es = Elasticsearch(http_auth=(username, password))
    xpack = XPackClient(es)

    print("Configure ltr_admin role:")
    res = xpack.security.put_role('ltr_admin', {
        "cluster": ["all"],
        "indices": [ {
            "names": [".ltrstore*"],
            "privileges": ["all"]
        } ]
    })
    print(res)

    print("Configure tmdb role:")
    res = xpack.security.put_role('tmdb', {
        'indices': [ {
            "names": ["tmdb"],
            "privileges": ["all"]
        } ]
    })
    print(res)

    print("Configure ltr_demo user:")
    res = xpack.security.put_user('ltr_demo', {
        'password': 'changeme',
        'roles': ['ltr_admin', "tmdb"]
    })
    print(res)

    print("\nRoles and user created. Be sure to update settings.cfg")

