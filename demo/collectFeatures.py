
logQuery = {
    "size": 100,
    "query": {
        "bool": {
            "must": [
                {
                    "terms": {
                        "_id": ["7555"]

                    }
                }
            ],
            "should": [
                {"sltr": {
                    "_name": "logged_featureset",
                    "featureset": "movie_features",
                    "params": {
                        "keywords": "rambo"
                    }
                }}
                ]
            }
    },
    "ext": {
        "ltr_log": {
            "log_specs": {
                "name": "main",
                "named_query": "logged_featureset",
                "missing_as_zero": True

            }
        }
    }
}

def featureDictToList(ranklibLabeledFeatures):
    rVal = [0.0] * len(ranklibLabeledFeatures)
    for ranklibIdx, value in ranklibLabeledFeatures.items():
        try:
            rVal[int(ranklibIdx) - 1] = value
        except IndexError:
            import pdb; pdb.set_trace()
            print("Out of range %s" % ranklibIdx)
    return rVal



def logFeatures(es, judgmentsQid):
    for qid, judgments in judgmentsQid.items():
        keywords = judgments[0].keywords
        docIds = [judgment.docId for judgment in judgments]
        logQuery['query']['bool']['must'][0]['terms']['_id'] = docIds
        logQuery['query']['bool']['should'][0]['sltr']['params']['keywords'] = keywords
        res = es.search(index='tmdb', body=logQuery)
        # Add feature back to each judgment
        featuresPerDoc = {}
        for doc in res['hits']['hits']:
            docId = doc['_id']
            features = doc['fields']['_ltrlog'][0]['main']
            featuresPerDoc[docId] = featureDictToList(features)

        # Append features from ES back to ranklib judgment list
        for judgment in judgments:
            try:
                features = featuresPerDoc[judgment.docId] # If KeyError, then we have a judgment but no movie in index
                judgment.features = features
            except KeyError:
                print("Missing movie %s" % judgment.docId)


if __name__ == "__main__":
    from judgments import judgmentsFromFile, judgmentsByQid
    from elasticsearch import Elasticsearch
    es = Elasticsearch()
    judgmentsQid = judgmentsByQid(judgmentsFromFile('sample_judgments.txt'))
    logFeatures(es, judgmentsQid)
    for qid, judgmentList in judgmentsQid.items():
        for judgment in judgmentList:
            print(judgment.toRanklibFormat())

