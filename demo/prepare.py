from urllib.parse import urljoin
import requests

def downloadLtrResource(resource):
    ltrDomain = 'http://es-learn-to-rank.labs.o19s.com/'
    resourceUrl = urljoin(ltrDomain, resource)
    with open(resource, 'wb') as dest:
        print("GET %s" % resourceUrl)
        resp = requests.get(resourceUrl, stream=True)
        for chunk in resp.iter_content(chunk_size=1024):
            if chunk:
                dest.write(chunk)


if __name__ == "__main__":
    downloadLtrResource('tmdb.json')
    downloadLtrResource('RankLib-2.8.jar')
