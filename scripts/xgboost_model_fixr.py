#!/usr/bin/env python

"""This is a script for fixing XGBoost boosting trees which have negative scores.
    We calculate the most negative leaf value, and append one more tree to the model
    which adds the abs() of this smallest value, meaning we always get a positive score,
    but relatively the scores will not change.
"""

import json
import sys
import argparse
import logging


def find_min(tree):
    """Finds the minimum leaf value in a tree
        Parameters
        ----------
        tree : dict
            parsed model
        """
    if 'leaf' in tree.keys():
        return tree['leaf']
    else:
        mapped = list(map(lambda t: find_min(t), tree['children']))
        return min(mapped)


# finds the first feature in a tree, we then use this in the split condition
# it doesn't matter which feature we use, as both of the leaves will add the same value
def find_first_feature(tree):
    """Finds the first feature in a tree, we then use this in the split condition
        It doesn't matter which feature we use, as both of the leaves will add the same value
        Parameters
        ----------
        tree : dict
            parsed model
        """
    if 'split' in tree.keys():
        return tree['split']
    elif 'children' in tree.keys():
        return find_first_feature(tree['children'][0])
    else:
        raise Exception("Unable to find any features")


def create_correction_tree(correction_value, feature_to_split_on):
    """Creates new tree with the given correction amount
        Parameters
        ----------
        correction_value : float
            leaf values for new tree
        feature_to_split_on : string
            feature name for the new tree
        """
    return {
        "children": [
            {
                "leaf": correction_value,
                "nodeid": 1
            },
            {
                "leaf": correction_value,
                "nodeid": 2
            }
        ],
        "depth": 0,
        "missing": 1,
        "no": 2,
        "nodeid": 0,
        "split": feature_to_split_on,
        "split_condition": 1,
        "yes": 1
    }


def fix_tree(trees):
    """Calculate and return a tree that will provide a positive final score
        Parameters
        ----------
        trees : dict
            trees from model
        """
    summed_min_leafs = sum(map(lambda t: find_min(t), trees))
    correction_value = abs(summed_min_leafs)
    logging.info("Correction value: {}".format(correction_value))
    if summed_min_leafs < 0:
        feature_to_split_on = find_first_feature(trees[0])

        # define an extra tree that produces a positive value so that the sum of all the trees is > 0
        extra_tree = create_correction_tree(correction_value, feature_to_split_on)
        return extra_tree
    else:
        logging.info("Not modifying tree, scores are already positive")
        return None


#
def process(in_file, out_file):
    """Fixes input model and writes to output model
        Parameters
        ----------
        in_file : file
            model json file to read
        out_file : file
            model json file to write
        """
    with in_file as i:
        model = json.load(i)
        inner_model = 'definition' in model
        if inner_model:
            definition_string = model['definition']
        else:
            inner_model = False
            definition_string = model['model']['model']['definition']

        # parse the escaped string to a list of trees
        trees = json.loads(definition_string)

        correction_tree = fix_tree(trees)
        if correction_tree is not None:
            trees.append(correction_tree)

        # replace the definition and handle both json variants
        if inner_model:
            model['definition'] = json.dumps(trees)
        else:
            model['model']['model']['definition'] = json.dumps(trees)

        # save it to a new file
        with out_file as o:
            json.dump(model, o)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)

    parser = argparse.ArgumentParser(description="""Model fixr adds a tree to
    the model with a positive leaf score
    equal to the abs sum of the min leafs of the other trees.""")

    parser.add_argument('-i', '--input',
                        action='store', nargs='?',
                        help='Filename for the input model',
                        type=argparse.FileType('r'), default='model.json')

    parser.add_argument('-o', '--output',
                        action='store', nargs='?',
                        help='Filename for the modified model',
                        type=argparse.FileType('w'), default='model-fixed.json')

    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit(1)
    args = parser.parse_args()
    process(args.input, args.output)
