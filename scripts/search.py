from features import formatFeature

baseQuery = {
  "query": {
      "match": {
          "_all": "test"
       }
   },
  "rescore": {
      "query": {
        "rescore_query": {
            "ltr": {
                    "model": {
                        "stored": "" # Model name
                    },
                    "features": []# features]
            }
         }
      }
   }
}

def featureQueries(keywords):
    try:
        ftrId = 1
        while True:
            parsedJson = formatFeature(ftrId, keywords)
            baseQuery['rescore']['query']['rescore_query']['ltr']['features'].append(parsedJson['query'])
            ftrId += 1
    except IOError:
        pass
    import json
    baseQuery['query']['match']['_all'] = keywords
    print("%s" % json.dumps(baseQuery))
    return baseQuery


if __name__ == "__main__":
    from sys import argv
    from elasticsearch import Elasticsearch
    esUrl="http://localhost:9200"
    es = Elasticsearch(timeout=1000)
    search = featureQueries(argv[1])
    model = "test"
    if len(argv) > 2:
        model = argv[2]
    baseQuery['rescore']['query']['rescore_query']['ltr']['model']['stored'] = model
    results = es.search(index='tmdb', doc_type='movie', body=search)
    for result in results['hits']['hits']:
             print(result['_source']['title'])

