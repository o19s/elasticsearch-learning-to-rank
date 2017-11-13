import os
from collectFeatures import logFeatures, buildFeaturesJudgmentsFile
from loadFeatures import initDefaultStore, loadFeatures, appendFeatures
from search import search
import random


def trainModel(judgmentsWithFeaturesFile, modelOutput, whichModel=6):
    # java -jar RankLib-2.6.jar -ranker 6 -train sample_judgments_wfeatures.txt -save model.txt
    cmd = "java -jar RankLib-2.8.jar -ranker %s -train %s -save %s -frate 1.0 -feature features.txt" % (whichModel, judgmentsWithFeaturesFile, modelOutput)
    print("*********************************************************************")
    print("*********************************************************************")
    print("Running %s" % cmd)
    os.system(cmd)
    pass


def saveModel(esHost, scriptName, featureSet, modelFname):
    """ Save the ranklib model in Elasticsearch """
    import requests
    import json
    from urllib.parse import urljoin
    modelPayload = {
        "model": {
            "name": scriptName,
            "model": {
                "type": "model/ranklib",
                "definition": {
                }
            }
        }
    }

    with open(modelFname) as modelFile:
        modelContent = modelFile.read()
        path = "_ltr/_featureset/%s/_createmodel" % featureSet
        fullPath = urljoin(esHost, path)
        modelPayload['model']['model']['definition'] = modelContent
        print("POST %s" % fullPath)
        resp = requests.post(fullPath, json.dumps(modelPayload))
        print(resp.status_code)
        if (resp.status_code >= 300):
            print(resp.text)



def buildAModel(useFeatures=[], fName='model.txt'):
    with open('features.txt', 'w') as f:
        f.write("\n".join([str(ftrOrd+1) for ftrOrd, ftr in enumerate(useFeatures)]))
    # Parse a judgments
    movieJudgments = judgmentsByQid(judgmentsFromFile(filename='sample_judgments.txt'))
    # Use proposed Elasticsearch queries (1.json.jinja ... N.json.jinja) to generate a training set
    # output as "sample_judgments_wfeatures.txt"
    logFeatures(es, judgmentsByQid=movieJudgments)
    buildFeaturesJudgmentsFile(movieJudgments, filename='sample_judgments_wfeatures.txt')
    # Train each ranklib model type
    for modelType in [6]:
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
        trainModel(judgmentsWithFeaturesFile='sample_judgments_wfeatures.txt', modelOutput=fName, whichModel=modelType)
        saveModel(esHost=esUrl, scriptName="test_%s" % modelType, featureSet='movie_features', modelFname=fName)


def pickFeatures(availableFeatures):
    otherFeatures = []
    initialFeatures = random.sample(availableFeatures, random.randint(1,3))
    for ftr in availableFeatures:
        add = random.randint(0,1)
        if add == 1 and ftr not in initialFeatures:
            otherFeatures.append(ftr)

    print("Initial %s" % initialFeatures)
    print("Other %s" % otherFeatures)
    return initialFeatures, otherFeatures



if __name__ == "__main__":
    import configparser
    from elasticsearch import Elasticsearch
    from sys import exit
    from judgments import judgmentsFromFile, judgmentsByQid

    config = configparser.ConfigParser()
    config.read('settings.cfg')
    esUrl = config['DEFAULT']['ESHost']

    es = Elasticsearch(esUrl, timeout=1000)

    availableFeatures = ['titleScore', 'overviewScore', 'taglineScore', 'userRating']

    for i in range(0,100):
        # Load features into Elasticsearch
        initDefaultStore(esUrl)

        initialFeatures, otherFeatures = pickFeatures(availableFeatures)
        loadFeatures(esUrl, loadFeatures=initialFeatures)
        appendFeatures(esUrl, loadFeatures=otherFeatures)

        allUsedFeatures = initialFeatures + otherFeatures
        trainFeatures = random.sample(allUsedFeatures, random.randint(1,3))

        buildAModel(useFeatures=trainFeatures, fName='orig_model.txt')
        search(es, model='test_6', keyword='rambo foo')



    # Load features into Elasticsearch
    initDefaultStore(esUrl)

    otherFeatures = []
    initialFeatures = random.sample(range(4),random.randint(1,3))
    for i in range(4):
        add = random.randint(0,1)
        if add == 1 and i not in initialFeatures:
            otherFeatures.append(i)

    print("Initial %s" % initialFeatures)
    print("Other %s" % otherFeatures)

    loadFeatures(esUrl, loadFeatures=initialFeatures)
    appendFeatures(esUrl, loadFeatures=otherFeatures)

    saveModel(esHost=esUrl, scriptName="test_6", featureSet='movie_features', modelFname='orig_model.txt')
