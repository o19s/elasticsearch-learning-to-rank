import json

def enrich(movie):
    """ Enrich for search purposes """
    if 'title' in movie:
        movie['title_sent'] = 'SENTINEL_BEGIN ' + movie['title']

def reindex(es, analysisSettings={}, mappingSettings={}, movieDict={}, index='tmdb'):
    import elasticsearch.helpers
    settings = {
        "settings": {
            "number_of_shards": 1,
            "index": {
                "analysis" : analysisSettings,
            }}}

    if mappingSettings:
        settings['mappings'] = mappingSettings #C

    es.indices.delete(index, ignore=[400, 404])
    es.indices.create(index, body=settings)

    def bulkDocs(movieDict):
        for id, movie in movieDict.items():
            if 'release_date' in movie and movie['release_date'] == "":
                del movie['release_date']
            enrich(movie)
            addCmd = {"_index": index, #E
                      "_type": "movie",
                      "_id": id,
                      "_source": movie}
            yield addCmd
            if 'title' in movie:
                print("%s added to %s" % (movie['title'], index))

    elasticsearch.helpers.bulk(es, bulkDocs(movieDict))

if __name__ == "__main__":
    from utils import Elasticsearch
    from sys import argv

    es = Elasticsearch(timeout=30)
    movieDict = json.loads(open('tmdb.json').read())
    reindex(es, movieDict=movieDict)
