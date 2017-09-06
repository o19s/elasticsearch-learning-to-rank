import xgboost as xgb

# Read the LibSVM labels/features
dtrain = xgb.DMatrix('xgboost.txt')
param = {'max_depth':2, 'eta':1, 'silent':1, 'objective':'reg:linear'}
num_round = 2

bst = xgb.train(param, dtrain, num_round)

model = bst.get_dump(fmap='featmap.txt', dump_format='json')

with open('xgb-model.json', 'w') as output:
    output.write('[' + ','.join(list(model)) + ']')
    output.close()

