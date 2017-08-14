import os
from features import kwDocFeatures, buildFeaturesJudgmentsFile


def trainModel(judgmentsWithFeaturesFile, modelOutput, whichModel=6):
    # java -jar RankLib-2.6.jar -ranker 6 -train sample_judgements_wfeatures.txt -save model.txt
    cmd = "java -jar RankLib.jar -ranker %s -train %s -save %s -frate 1.0" % (whichModel, judgmentsWithFeaturesFile, modelOutput)
    print("*********************************************************************")
    print("*********************************************************************")
    print("Running %s" % cmd)
    os.system(cmd)
    pass


def saveModel(es, scriptName, modelFname):
    """ Save the ranklib model in Elasticsearch """
    with open(modelFname) as modelFile:
        modelContent = modelFile.read()
        es.put_script(lang='ranklib', id=scriptName, body={"script": modelContent})





if __name__ == "__main__":
    import configparser
    from elasticsearch import Elasticsearch
    from judgments import judgmentsFromFile, judgmentsByQid

    config = configparser.ConfigParser()
    config.read('settings.cfg')
    esUrl = config['DEFAULT']['ESHost']

    es = Elasticsearch(esUrl, timeout=1000)
    # Parse a judgments
    judgements = judgmentsByQid(judgmentsFromFile(filename='sample_judgements.txt'))
    # Use proposed Elasticsearch queries (1.json.jinja ... N.json.jinja) to generate a training set
    # output as "sample_judgements_wfeatures.txt"
    kwDocFeatures(es, index='tmdb', searchType='movie', judgements=judgements)
    buildFeaturesJudgmentsFile(judgements, filename='sample_judgements_wfeatures.txt')
    # Train each ranklib model type
    for modelType in [0,1,2,3,4,6,7,8,9]:
        # 0, MART
        # 1, RankNet
        # 2, RankBoost
        # 3, AdaRank
        # 4, coord Ascent
        # 6, LambdaMART
        # 7, ListNET
        # 8, Random Forests
        # 9, Linear Regression
        print("*** Training %s " % modelType)
        trainModel(judgmentsWithFeaturesFile='sample_judgements_wfeatures.txt', modelOutput='model.txt', whichModel=modelType)
        saveModel(es, scriptName="test_%s" % modelType, modelFname='model.txt')
