from lxml import etree

def parseSplit(split):
    fullSplit = {}
    for splitChild in split:
        if (splitChild.tag == "output"):
            return {"output": float(splitChild.text)}
        elif (splitChild.tag == "feature"):
            fullSplit['feature'] = splitChild.text
        elif (splitChild.tag == "threshold"):
            fullSplit['threshold'] = float(splitChild.text)
        elif (splitChild.tag == "split"):
            if splitChild.attrib['pos'] == 'left':
                fullSplit['lhs'] = parseSplit(splitChild)
            elif splitChild.attrib['pos'] == 'right':
                fullSplit['rhs'] = parseSplit(splitChild)

    return {"split": fullSplit}





def readTree(treeNode):
    treeWeight = treeNode.attrib['weight']
    treeId = treeNode.attrib['id']
    singleSplit = [child for child in treeNode]
    assert(len(singleSplit) == 1)
    return {"split": parseSplit(singleSplit[0])["split"],
            "weight": treeWeight,
            "id": treeId}



def readEnsembles(modelFname="model.txt"):
    tree = etree.parse(modelFname)

    ensembles = []

    for ensemble in tree.getroot():
        ens = {"ensemble": []}
        for decTree in ensemble:
            tre = readTree(decTree)
            ens["ensemble"].append(tre)

        ensembles.append(ens)
    return {"forest": ensembles}

if __name__ == "__main__":
    import json
    print(json.dumps(readEnsembles("big-model.txt"), indent=4))
