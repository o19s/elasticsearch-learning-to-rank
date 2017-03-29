from features import formatFeature

baseQuery = {
  "query": {
        "ltr": {
                "model": {
                    "stored": "" # Model name
                },
                "features": []# features]
        }
  }
}

def featureQueries(keywords):
    try:
        ftrId = 1
        while True:
            parsedJson = formatFeature(ftrId, keywords)
            baseQuery['query']['ltr']['features'].append(parsedJson['query'])
            ftrId += 1
    except IOError:
        pass
    import json
    print("%s" % json.dumps(baseQuery))
    return baseQuery


if __name__ == "__main__":
    from sys import argv
    from elasticsearch import Elasticsearch
    esUrl="http://localhost:9200"
    es = Elasticsearch(timeout=1000)
    search = featureQueries(argv[1])
    model = "test_6"
    if len(argv) > 2:
        model = argv[2]
    baseQuery['query']['ltr']['model']['stored'] = model
    results = es.search(index='tmdb', doc_type='movie', body=search)
    for result in results['hits']['hits']:
             print(result['_source']['title'])

